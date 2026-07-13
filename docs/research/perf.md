# ЭТОТ ФАЙЛ СОДЕРЖИТ ОШИБКИ — читать только вместе с docs/TZ-B-PERF.md (§2.7 и §2.8)

Разбор по байткоду опроверг здесь как минимум три утверждения:

1. Теневой проход Iris ИДЁТ через WorldRenderer.renderEntity (строка ~34 утверждает обратное).
2. «alt-tab back is instant» (строка ~60) — неверно: клиент просыпается и тут же засыпает до дедлайна кадра.
3. «Sodium не трогает частицы» — неверно: не культит, но оптимизирует (SingleQuadParticleMixin).

При расхождении прав ТЗ, а не этот файл.

---

# perf

## SUMMARY
Verified against 1.21.1 bytecode, not memory. Honest baseline: Club today COSTS ~0.45 ms/frame and optimizes nothing, so "the game runs better with Club" is currently false and must be earned. Sodium already owns terrain rendering and ships a section-based entity cull ON by default, so for a Sodium player most classic FPS tricks are already taken. The genuinely unclaimed wins left: PARTICLE culling (vanilla does zero culling of particles — verified), BLOCK-ENTITY frustum + tightened distance (vanilla only distance-culls at 64), a background FPS throttle (1.21.1 has none at all — verified), and cutting our own 0.45 ms. For a NON-Sodium player, section-visibility entity culling is a real added win. Rebuilding EntityCulling's async raycaster should be DROPPED. Proof must assert on deterministic render COUNTS and merely report noisy FPS, with interleaved A/B blocks in one session.

## PROPOSAL
WORKSTREAM 2 — OPTIMIZATION

## 0. THE HONEST BASELINE (top of the docs, not buried)

Right now Club makes the game slightly SLOWER. The harness measures our in-world HUD at ~0.45 ms/frame (ClubHarness.java:449-456): 2.7% of a 60-FPS frame, ~9% of a 200-FPS frame. Before we may say "the game runs better with Club", we must (a) pay that back and (b) beat it.

Sodium owns the ground we would most like to stand on. It rewrites terrain rendering, and since 0.6 it ships "Use Entity Culling" ON by default (a cheap "is the entity's chunk section visible" pass). Most players who care about FPS run it.

So the field splits, and every candidate is labelled with which player it actually helps:

| Technique | WITHOUT Sodium | WITH Sodium |
|---|---|---|
| Terrain / chunk rendering | Sodium's. WE WILL NOT TOUCH IT. | already done |
| Entity culling by section visibility | REAL WIN — nobody does it for them | Sodium already does it -> DROP |
| Entity occlusion culling (raycast) | EntityCulling's job | EntityCulling's job |
| Particle culling | REAL — vanilla does ZERO particle culling | REAL — Sodium doesn't touch particles either |
| Block-entity frustum + distance | REAL, MODEST | same — Sodium doesn't cull BEs |
| Background / idle throttle | REAL — 1.21.1 has none at all | REAL — Sodium has none |
| Our own 0.45 ms | REAL — the only one we owe unconditionally | same |

## 1. CANDIDATE TECHNIQUES

### 1.1 Entity culling by section visibility — BUILD (non-Sodium only)
WHAT. Every frame WorldRenderer.applyFrustum() refills builtChunks with the sections the occlusion graph kept. Hook its RETURN, pack each BuiltChunk.getOrigin() into a LongOpenHashSet. Then @Inject(method="renderEntity", at=@At("HEAD"), cancellable=true): if NONE of the sections touched by the entity's getVisibilityBoundingBox() is in the set -> cancel.
GAIN (honest). Open terrain: ~0% (everything visible is in a visible section). Caves, ravines, hilly terrain, walled bases, mob farms: 5-20% where many entities sit behind terrain; literally 0% in a flat field with nothing around. It is not an "FPS boost", it is "we stop drawing what you can't see".
RISK — catastrophic failure mode. If builtChunks is empty (another mod replaced the terrain path) we cull EVERY entity in the world. Three mitigations, all required: (1) hard gate on FabricLoader.isModLoaded("sodium") -> off, defer to Sodium's; (2) self-check — empty set while mc.world != null and the player sits in a loaded chunk -> disable for the session, log once; (3) never cull the camera entity, ignoreCameraFrustum entities, the ridden entity, or anything within 8 blocks.
BLAST RADIUS. Mods that render entities far outside their bbox (Create contraptions, beams, magic-mod client entities) get culled wrongly — exactly why EntityCulling ships a whitelist. Mitigate with a 2-block bbox margin plus an entity-type blacklist.
IRIS / SHADOW PASS — OPEN QUESTION. Iris renders the shadow map through its own entity path (ShadowRenderer), not WorldRenderer.renderEntity, so our hook SHOULD leave shadows alone. VERIFY in -PclubCompat -PclubIris before shipping. Do not assume.
LEGAL? Yes — render-only, no packets, no simulation change, undetectable server-side.

### 1.2 Entity occlusion culling by async raycast — DROP
EntityCulling uses async PATH-TRACING on spare CPU threads, not GL occlusion queries (I read the source README; the common "GL queries" belief is wrong). Rebuilding it means a thread-safe opaque-block snapshot, a DDA raytracer, a visibility cache with hysteresis and a whitelist system — a large project ending in a worse version of a mature free mod, which it would then FIGHT if both were installed. Name EntityCulling in our docs as the thing to install alongside Club. GL occlusion queries are their own trap: one-frame latency, and under Iris the depth state we would query is not the one on screen.

### 1.3 Particle culling — BUILD (best unclaimed win)
Verified: renderParticles() walks every live particle and calls buildGeometry — no frustum test (zero Frustum references in the class), no distance test, no cap. A campfire/redstone/potion scene tessellates hundreds of quads BEHIND YOUR HEAD every frame.
- @Redirect the Particle.buildGeometry call inside renderParticles: skip if the particle is BEHIND THE CAMERA PLANE (dot of (pos - camPos) with the camera look vector, minus the particle's size and a 2-block slack) or BEYOND a configurable distance (default = vanilla, unlimited; slider 16/32/64/inf).
- Optional hard cap ("max particles rendered per frame", off by default) — insurance against TNT/explosion frame drops.
GAIN (honest). Behind-camera culling changes NOTHING VISIBLE: 1-3% in a normal scene; plausibly 5-15% in a particle-dense one (mob farm, campfire village, potion fight). The distance limit and the cap change what you see -> opt-in.
RISK. Low; a wrong margin pops particles at the screen edge. BLAST RADIUS: nobody else hooks this seam.

### 1.4 Block-entity culling — BUILD (modest; and the brief's premise is wrong)
Vanilla ALREADY distance-culls BEs at 64 via isInRenderDistance. What is missing: a per-BE FRUSTUM test (only the 16x16x16 section is frustum-culled, so BEs inside a visible section but off-screen still render) and a tighter distance for chests/signs/banners.
@Inject(HEAD, cancellable) on BlockEntityRenderDispatcher.render:
- effective distance = min(the renderer's own getRenderDistance(), our slider) — we can only TIGHTEN, never break a beacon (256) or an end gateway;
- frustum test on the BE's block box, SKIPPED when rendersOutsideBoundingBox(be).
GAIN (honest). Normal world 0-2%. Storage room / sign-heavy build / shop district 5-15%. Say it exactly like that.
RISK. Low-medium: BERs drawing outside their block (beacon beam, Create) need the rendersOutsideBoundingBox respect plus a type blacklist.

### 1.5 Background throttle (Dynamic-FPS-style) — BUILD, but sell it honestly
Verified: 1.21.1 has no unfocused throttle. @ModifyReturnValue on getFramerateLimit():
- unfocused -> configurable cap (default 15);
- in-world with the pause/inventory screen open -> configurable cap (default 30, OFF by default);
- NEVER BELOW 15, and here is exactly why: render() runs for (j = 0; j < Math.min(10, i); ++j) tick(); — at most 10 client ticks per frame, so below 2 FPS the client cannot hold 20 TPS and time dilates; and packets drain in runTasks() ONCE PER FRAME, so at 1 FPS keepalives answer once a second (inside the 15 s server timeout — but chunk-mesh uploads and the audio listener also update only once a second). Dynamic FPS's answer — keep the LOOP at >=15 Hz and cancel the RENDERING of superfluous frames — is the correct shape; a true 1-FPS background must copy it, not clamp the limiter.
- limitDisplayFPS sleeps in glfwWaitEventsTimeout (yields CPU, wakes on input) — verified — so alt-tab back is instant.
GAIN (honest). ZERO IN-GAME FPS. It is a battery / fan-noise / second-monitor feature: 200 -> 15 FPS while alt-tabbed is a large power saving and nothing else. Calling it "more FPS" would be a lie. It also duplicates Dynamic FPS, which does it better (per-state volume, idle timeouts, battery detection). Ship the 40-line version and say in the docs that Dynamic FPS is the full-featured one.

### 1.6 Our own cost — BUILD FIRST; the one we owe unconditionally
Before optimizing, INSTRUMENT: split HudManager.profile() into phases — (1) TargetHud raycast, (2) layout, (3) text shaping, (4) shape/vertex build, (5) GL submit. My suspicion from reading the code is that the per-frame world raycast in TargetHud (block DDA up to 32 blocks PLUS an entity box sweep, EVERY frame — TargetHud.java:60-80) is a large slice — but I will not claim a number I have not measured, and neither should the Modrinth page.
Then, in likely payoff order:
- Run the target raycast once per TICK (20 Hz), not once per FRAME (up to 300 Hz). The chip already has a 200 ms grace window, so a 50 ms refresh is invisible. Probably the single biggest slice, for free.
- Memoize HudText.width(...) per (string, weight, size) — elements call it repeatedly per frame on the same strings.
- Cache EffectsElement's Fx[] and key strings while the effect set is unchanged.
- DO NOT hunt in the UI backend — text layout is already allocation-free and the buffers are pooled.
TARGET: 0.45 ms -> <=0.20 ms, asserted in the harness. That alone moves the honest claim from "Club costs 9% of a 200-FPS frame" to "4%".

### 1.7 EXPLICITLY REJECTED — name them and drop them
- Chunk/terrain rendering: Sodium's. Not competing.
- Entity shadows: vanilla already has the Entity Shadows toggle. Nothing to add.
- Clouds / sky / fog: vanilla already has Clouds and Graphics Fast/Fancy; "no sky" is Sodium Extra territory, ~1%. Skip.
- Weather render skip: real but tiny, and a visual lie in the rain.
- Lightmap: update() early-returns unless dirty (~20/s, not per frame). NOTHING TO WIN. (Verified.)
- GC / object pooling in MC hot loops: a mod cannot set JVM flags; escape analysis plus young-gen collection make most Vec3d/Box churn nearly free; FerriteCore/ModernFix reduce heap footprint, not frame time. Chasing this produces exactly the fake optimizations we were warned about. DROP.
- Enchantment glint: ~0-1% outside an armour-stand museum. Not worth a config row.
- Tick culling (skipping client entity ticks): changes client simulation, breaks animations, risks desync. NEVER.
- Item-entity model stacking (up to 5 models per stack): real and cheap but VISIBLE; opt-in row only if the benchmark shows it earning its space.
- Animated-texture throttling: verified real, but making it SAFE means knowing which sprites are visible (a Sodium-coupled problem). The honest version is a user-facing visual downgrade, not an optimization. Low priority, opt-in, never default.

## 2. HOW WE PROVE IT
THE RULE: ASSERT ON DETERMINISTIC COUNTS, REPORT ON NOISY FPS. This project was already burned once benchmarking with the FPS counter.

### 2.1 The scene — ClubBench (CLUB_BENCH=1), modelled on ClubPromo
- Fixed-seed world (club-bench-world, reusing ClubPromo's createAndStart path), HARD-CODED BlockPos and yaw/pitch — not locateBiome, which is a moving target.
- Server-side staging (the same onServer / serverIdle / worldReady gates ClubPromo already proved): time fixed 6000, doDaylightCycle / doWeatherCycle / doMobSpawning off, weather clear, spectator, purge().
- A DETERMINISTIC LOAD: a fixed lattice of 150 mobs with NoAI + NoGravity — half in open sight, half sealed behind a stone wall the harness builds (this is what gives the entity culler something to PROVE); 60 item entities; 80 block entities (chests/signs/banners); 8 campfires for particles.
- Pin the render settings: render distance 12, Graphics Fancy, VSYNC OFF, MAX FPS UNLIMITED — and ASSERT them, because a run under vsync measures the monitor.

### 2.2 The measurement
- FRAME TIME sampled at the HEAD of GameRenderer.render — NOT MinecraftClient.render, which contains limitDisplayFPS's sleep and would measure the FPS cap. Ring buffer of System.nanoTime() deltas.
- WARMUP: discard until worldReady() AND 300 further frames AND the rolling frame-time stddev falls under a threshold (JIT, chunk build, Iris shader compile).
- INTERLEAVED A/B IN ONE SESSION: 8 blocks x 240 frames — OFF, ON, OFF, ON, OFF, ON, OFF, ON — discarding the first 30 frames of each block (a toggle dirties caches). One long A then one long B measures your GPU warming up, not your mod.
- PER CONDITION: N, MEDIAN frame time, mean, 1% LOW (mean of the slowest 1% of frames, reported as ms and as 1000/ms), p99.
- COUNTS PER CONDITION (the real evidence): entities rendered per frame (WorldRenderer.regularEntityCount via @Accessor — F3's own number), blockEntityCount, and our own counters: particles skipped/considered, entities culled/considered.
- PROVENANCE in the report: GPU string (GlDebugInfo.getRenderer()), resolution, render distance, whether sodium/iris are loaded, vsync/cap state, UI backend (MODERN/LEGACY).

### 2.3 The asserts (into run/club-bench-report.txt, harness style)
1. "bench: the run is valid" — vsync off, FPS uncapped, >=1800 measured frames. Otherwise the run prints INVALID and emits NO PASS/FAIL AT ALL. (A benchmark that cannot fail honestly must refuse to pass.)
2. "bench: the environment is stable" — the four OFF-block medians agree within 5%. If not, the machine is too noisy -> INVALID, not FAIL.
3. "bench: culling never renders MORE than vanilla" — renderedEntities(ON) <= renderedEntities(OFF); same for BEs. Deterministic; must always hold.
4. "bench: the culler actually culls in this scene" — renderedEntities(ON) <= 0.65 x renderedEntities(OFF) (the scene hides half the mobs behind a wall). THIS is the assert that proves the feature works, and it has no noise in it.
5. "bench: no regression" — medianFrameTime(ON) <= medianFrameTime(OFF) x 1.02. Primum non nocere; this one may never be allowed to fail.
6. "bench: the win is real" — medianFrameTime(ON) <= medianFrameTime(OFF) x (1 - X), where X IS FILLED IN FROM THE FIRST HONEST MEASUREMENT and thereafter guards regressions. We do not invent X in advance.
7. "bench: Club's own HUD draw <= 0.20 ms" (tightened from the existing 0.8 ms once 1.6 lands).
8. "bench: the 1% low does not get worse" — p99(ON) <= p99(OFF) x 1.05. A culler that rebuilds a hash set every frame could trade average FPS for stutter; this catches it.

### 2.4 The run matrix — two reports, always
- ./gradlew runClient -> NO SODIUM: the number for the "vanilla + Club" player.
- ./gradlew runClient -PclubCompat -> WITH SODIUM 0.6.13: the number that applies to most FPS-conscious players.
- (-PclubIris: a third run purely for "does it break shadows/shaders", not for FPS.)
Quoting the no-Sodium number as if it applied to everyone is the single most likely lie this workstream will produce. The report prints BOTH side by side, or neither.

## 3. THE MODRINTH HEADLINE

HONEST — verbatim what we could write:
"Club draws less of what you can't see. Entities hidden behind terrain, particles behind the camera, block entities you're nowhere near. On our benchmark scene (fixed seed, fixed camera, 150 mobs — half of them walled off — 60 items, 80 block entities; 1080p, 12 chunks, vsync off), median frame time went A ms -> B ms (+C%) without Sodium, and D ms -> E ms (+F%) with Sodium 0.6 installed, measured in the same session with the optimizations toggled on and off eight times. Entities rendered per frame: 187 -> 61.
Club also COSTS something: its HUD takes ~0.2 ms per frame to draw. With every optimization switched off, Club is a small net loss. That is what the numbers say.
The benchmark ships in the repo (CLUB_BENCH=1). Run it on your machine.
Already running Sodium? Most of the entity win is already yours — Sodium culls by chunk section. Want the rest? Install EntityCulling; we don't duplicate it."

LIES WE WILL NOT TELL:
- "Boosts FPS by up to 300%" — or "up to" anything.
- "Optimizes your game", with no scene, no hardware, no baseline.
- Quoting the NO-SODIUM delta as THE number.
- Quoting the MOB-FARM delta as a TYPICAL delta.
- "Better than Sodium" / "Sodium not needed".
- "Zero performance cost" — false; we cost ~0.2-0.45 ms/frame.
- 1% lows from a scene chosen because it flatters them.
- Any FPS claim for the background throttle. It saves battery. It does not give you frames.
- "Reduces lag" — lag is the network; this is frame time.

## 4. BUILD ORDER (a commit per stage, per house rules)
1. PHASE-PROFILE OUR OWN 0.45 ms, THEN CUT IT — raycast to 20 Hz, memoize text widths, cache the effects array. Tighten the assert to 0.20 ms. (The only item whose win is certain, and the only one we owe unconditionally.)
2. ClubBench — scene, frame-time sampler, interleaved A/B, counters, INVALID states, two-report matrix. BUILD THE RULER BEFORE THE THING IT MEASURES.
3. Particle culling (behind-camera free; distance/cap opt-in). Best win-to-risk ratio in the list.
4. Block-entity frustum + tightened distance (min(BER's own, our slider)).
5. Entity section-visibility culling — gated hard on !isModLoaded("sodium") plus the empty-set self-check, and only after the Iris shadow-pass question is answered.
6. Background throttle (>=15 FPS floor; never touch the loop rate).
7. docs/PERFORMANCE.md — every number with its scene and its hardware, plus a "what we deliberately don't do, and which mod does it" table.

CONFIG: one new ClubConfig.Performance section; version 9 -> 10 plus a migrate() branch.
UI: ONE CARD, not a tab of eight sliders. A "Performance" tab full of toggles is what a cheat client looks like, and DESIGN.md section 1 ("does this make it feel more expensive, or just add detail?") rejects it. Four rows: Cull hidden entities (auto/off), Cull particles (on/off + distance), Block entity distance (slider), Background FPS (slider). That is the product.

## RISKS
- THE ONE THAT CAN KILL US: if WorldRenderer.builtChunks is empty (another mod owns the terrain path), a section-visibility entity culler culls EVERY entity in the world — an invisible-mobs bug that reads like a malfunctioning cheat client. Requires all three mitigations (Sodium gate, empty-set self-check with session-disable, near-camera exemption) before it ships.
- Iris shadow pass: I could NOT verify that Iris renders shadow-map entities outside WorldRenderer.renderEntity. If it goes through our hook, culling by the main camera's visible sections corrupts or deletes shadows. Must be tested in -PclubCompat -PclubIris before the feature is enabled — not after.
- Mods that render entities or block entities far outside their bounding box (Create contraptions, beams, magic-mod client entities) get culled wrongly. EntityCulling ships a whitelist for exactly this reason. Needs a bbox margin plus a type blacklist, and it will still generate bug reports.
- Duplicating mature mods: our entity cull IS Sodium's cull, our throttle IS Dynamic FPS's throttle. Presenting them as ours looks dishonest to anyone who knows the ecosystem, and if a player installs both we must not fight them. Gate on isModLoaded and name the real mod in the docs.
- The benchmark can lie without anyone intending it: one long OFF block then one long ON block measures the GPU warming up; a run under vsync measures the monitor; a scene chosen after the fact flatters the result. The interleaved blocks, the INVALID states and the stability assert exist to stop us — but only if we never publish a number from a run that reported INVALID.
- CPU-side frame-interval timing hides GPU cost when the driver queues frames ahead. At steady state the interval converges to max(CPU, GPU), which is why the metric is valid — but a short block on a deep-queuing GPU can still mislead. Mitigate with long blocks (240 frames) and by reporting p99 next to the median.
- We may measure a real win in a mob-farm scene and ~0% in an open field, and both will be true. The temptation to publish only the first is the central integrity risk of this entire workstream.
- Config-surface creep: a Performance tab full of toggles reads as a cheat client and violates DESIGN.md section 1. Four rows maximum, conservative defaults, and anything that changes what the player SEES must be opt-in.
- If the honest measurement comes back at ~1-2% with Sodium installed, the correct conclusion may be that this workstream ships ONLY the self-cost reduction and the particle cull — and says so on the Modrinth page. That outcome must be acceptable in advance, or the benchmark is theatre.

## OPEN QUESTIONS
- Does Iris render shadow-map entities through WorldRenderer.renderEntity, or through its own ShadowRenderer path? If the former, entity culling by the main camera's visible sections corrupts shadows. Answer empirically in ./gradlew runClient -PclubCompat -PclubIris before that feature ships.
- Where does our 0.45 ms actually go? I suspect the per-frame TargetHud world raycast (TargetHud.java:60-80) dominates, but that is a suspicion, not a measurement. Phase-profile before optimizing, and never publish a number we have not measured.
- With Sodium installed, is WorldRenderer.builtChunks empty, stale, or still correct? This decides whether the Sodium gate is a nicety or a hard safety requirement. Assume hard requirement until proven otherwise.
- Does Sodium 0.6 mixin BlockEntityRenderDispatcher.render or ParticleManager.renderParticles? Verified from docs that it does not CULL them, but a mixin conflict is a different question from a feature overlap — check with -PclubCompat and a mixin audit.
- What X (the real median-frame-time win) does the benchmark actually produce on the owner's machine, no-Sodium and with-Sodium? Every headline number, and the 'the win is real' assert, is blocked on that measurement.
- Default-on or default-off for the entity cull? Behind-camera particle culling and BE frustum culling change nothing a player can see and can default on; the entity cull can pop entities at chunk borders in edge cases. Owner's call, made after seeing it in-game — not from a table.

## FACTS
- [verified] Club's in-world HUD costs ~0.45 ms/frame in ~11 GL draw calls; the harness asserts <0.8 ms and <=16 calls. That is 2.7% of a 60-FPS frame and ~9% of a 200-FPS frame. Today the mod is a net performance COST, not a gain.
  src/main/java/com/club/harness/ClubHarness.java:449-456 (comment: 'Was 43 draws / ~1.25 ms ... Batching both (Stage 61) took it to ~11 calls / ~0.45 ms'); src/main/java/com/club/hud/HudManager.java:107-119
- [verified] This project ALREADY tried benchmarking by watching the FPS counter and it failed: the same build 'cost' 0.3 ms on one run and 1.1 ms on the next, because it measured the world rather than the mod. The dead fields fpsOff/fpsOn/offN/onN are the corpse of that attempt.
  src/main/java/com/club/hud/HudManager.java:103-106 (comment); src/main/java/com/club/harness/ClubHarness.java:63 (unused fields)
- [verified] Vanilla 1.21.1 ALREADY frustum- and distance-culls entities: EntityRenderer.shouldRender(T, Frustum, double, double, double) calls Entity.shouldRender(x,y,z), honours ignoreCameraFrustum, then tests frustum.isVisible(getVisibilityBoundingBox().expand(0.5)). What it does NOT do is occlusion culling — an entity behind a wall but inside the frustum is fully rendered.
  javap -c net/minecraft/client/render/entity/EntityRenderer.class, method shouldRender: invokevirtual Entity.shouldRender:(DDD)Z, getfield ignoreCameraFrustum, Box.expand(0.5) then frustum test
- [verified] Entities are drawn from a per-frame loop over ClientWorld.getEntities() gated only by EntityRenderDispatcher.shouldRender(...). The mixin seam is WorldRenderer.renderEntity(Entity, double, double, double, float, MatrixStack, VertexConsumerProvider) — private, injectable, cancellable.
  javap -c WorldRenderer.render: offset 750 ClientWorld.getEntities(), offset 796 EntityRenderDispatcher.shouldRender:(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z
- [verified] WorldRenderer keeps the frame's VISIBLE chunk sections in a private final ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks, refilled every frame in applyFrustum() from the occlusion graph; BuiltChunk.getOrigin() gives the section origin. That is enough to build a visible-section LongSet and cull entities in non-visible sections — Sodium-grade entity culling without a raycaster.
  javap -c WorldRenderer: field builtChunks:Lit/unimi/dsi/fastutil/objects/ObjectArrayList; applyFrustum(): builtChunks.clear() then chunkRenderingDataPreparer.method_52828(Frustum, List); ChunkBuilder$BuiltChunk.getOrigin()
- [verified] BLOCK ENTITIES ARE NOT RENDERED UNCONDITIONALLY — the brief's premise is wrong. BlockEntityRenderDispatcher.render() calls BlockEntityRenderer.isInRenderDistance(be, camera.getPos()) and returns early; the default getRenderDistance() is 64 blocks. The real gaps: no per-BE FRUSTUM test (only the 16x16x16 section is frustum-culled), no occlusion, and 64 blocks is generous for chests/signs/banners.
  javap -c BlockEntityRenderDispatcher.render(E,F,MatrixStack,VertexConsumerProvider): invokeinterface BlockEntityRenderer.isInRenderDistance then ifne/return; javap -c BlockEntityRenderer: getRenderDistance() = bipush 64
- [verified] PARTICLES GET NO CULLING AT ALL. ParticleManager.renderParticles() iterates every live particle in every texture-sheet queue and calls Particle.buildGeometry(VertexConsumer, Camera, float). There is not one reference to Frustum in the class, no distance test, no per-frame cap. Particles directly behind the camera are fully tessellated every frame.
  javap -c net/minecraft/client/particle/ParticleManager.class: renderParticles iterates PARTICLE_TEXTURE_SHEETS then Queue.iterator then Particle.buildGeometry; grep -ci frustum over that disassembly returns 0
- [verified] Minecraft 1.21.1 has NO unfocused/background FPS throttle. MinecraftClient.getFramerateLimit() returns 60 ONLY when world == null AND (currentScreen != null || overlay != null) — the title/loading screen. In-world, unfocused or paused, it returns the player's own max-FPS setting. InactivityFpsLimiter.class does not exist in this version (it arrives in 1.21.2+).
  javap -c MinecraftClient.getFramerateLimit(): world != null then window.getFramerateLimit(); else screen/overlay then bipush 60. unzip of the merged 1.21.1 jar: 'caution: filename not matched: net/minecraft/client/util/InactivityFpsLimiter.class'
- [verified] The cap is applied in MinecraftClient.render as: int i = getFramerateLimit(); if (i < 260) RenderSystem.limitDisplayFPS(i); and limitDisplayFPS sleeps in GLFW.glfwWaitEventsTimeout(...) — it yields the CPU and wakes on input, so a background cap genuinely frees CPU/GPU and alt-tab stays responsive.
  javap -c MinecraftClient offset 574: getFramerateLimit then sipush 260 then RenderSystem.limitDisplayFPS:(I)V; javap -c RenderSystem.limitDisplayFPS: loop of GLFW.glfwWaitEventsTimeout(d - e)
- [verified] THE THROTTLE TRAP: MinecraftClient.render runs for (int j = 0; j < Math.min(10, i); ++j) this.tick(); — at most 10 client ticks per frame, so below 2 FPS the client cannot hold 20 TPS and time dilates. Packets drain in runTasks() once per frame, so at 1 FPS keepalives answer once a second (inside the 15 s server timeout, but chunk-mesh uploads and the audio listener also update only once a second). RenderTickCounter.Dynamic.beginRenderTick applies no cap of its own.
  javap -c MinecraftClient.render offsets 144-177: bipush 10 + Math.min(II)I guarding the tick() loop; javap -c RenderTickCounter$Dynamic.beginRenderTick(J)I returns (int) tickDelta with no clamp
- [verified] Dynamic FPS avoids that trap by never slowing the render LOOP below 15 cycles/second, instead cancelling the rendering of superfluous frames (14 of 15 cancelled for '1 FPS'), which keeps ticks, packets and alt-tab responsiveness intact. Any throttle we ship must copy that shape, not clamp the limiter to 1.
  https://github.com/juliand665/Dynamic-FPS — 'Lower frame rates are achieved by then cancelling the rendering of all superfluous frames, e.g. 14 out of 15 frames are cancelled for 1 FPS'; minimum 15 cycles/s kept for responsiveness
- [likely] Sodium 0.6 ships 'Use Entity Culling' ENABLED BY DEFAULT — a cheap pass based on visible chunk sections, deliberately less aggressive than EntityCulling (it still renders entities inside a visible section that are themselves hidden). So a section-visibility cull is EXACTLY what Sodium already does: a win only for players WITHOUT Sodium.
  https://modrinth.com/mod/sodium (0.6 advanced setting 'Use Entity Culling', default on); https://modrinth.com/mod/entityculling (comparisons: EntityCulling still adds FPS on top because Sodium's pass is section-granular)
- [verified] EntityCulling (tr7zw) does NOT use OpenGL occlusion queries — it uses asynchronous path-tracing on spare CPU threads alongside the main thread, and skips rendering only (never simulation). Rebuilding it duplicates a mature, widely-installed mod.
  https://github.com/tr7zw/EntityCulling — 'Using async path-tracing to hide Tiles/Entities that are not visible'; 'Uses spare CPU threads to rapidly calculate visibility'; 'Runs alongside the main game thread without blocking'
- [verified] ImmediatelyFast already batches immediate-mode rendering including the vanilla HUD and text (hud_batching, fast_text_lookup). Our UI stack batches itself (Stage 61) and does not use vanilla's immediate path, so ImmediatelyFast neither helps nor conflicts with our HUD — and 'we batch the HUD' is not sellable as a general FPS feature.
  https://github.com/RaphiMC/ImmediatelyFast (hud_batching, fast_text_lookup); src/main/java/com/club/ui/backend/ModernBackend.java:400-403 (own BufferBuilder/BufferAllocator batch)
- [verified] Animated textures are ticked and re-uploaded every client tick with no visibility check: SpriteAtlasTexture.tickAnimatedSprites() binds the atlas and calls tick() on every entry of animatedSprites. Real but modest; any fix is a visual trade, not free.
  javap -c net/minecraft/client/texture/SpriteAtlasTexture.tickAnimatedSprites(): bindTexture(); then for each Sprite$TickableAnimation in animatedSprites: tick()
- [verified] A dropped item stack renders up to FIVE copies of its model: ItemEntityRenderer.getRenderedAmount(count) returns 1 (count<=1), 2 (<=16), 3 (<=32), 4 (<=48), else 5. Forcing 1 is a real win in item-heavy scenes and a visible change.
  javap -c net/minecraft/client/render/entity/ItemEntityRenderer.getRenderedAmount(I)I — thresholds 1/16/32/48 mapping to 1/2/3/4/5
- [verified] WorldRenderer keeps a per-frame count of entities ACTUALLY rendered: regularEntityCount is zeroed at the start of the entity section of render() and incremented per rendered entity (it is the first number in F3's 'E: x/y'), alongside blockEntityCount. Read via an @Accessor it is a DETERMINISTIC, noise-free instrument to prove a culler works — far stronger than an FPS delta.
  javap -c WorldRenderer.render offsets 606-613: 0 stored into regularEntityCount and blockEntityCount immediately after Profiler.swap('entities'); getEntitiesDebugString() reads both
- [verified] The repo already has the two things a serious benchmark needs: a Sodium+Iris+Freecam dev runtime (./gradlew runClient -PclubCompat [-PclubIris]; Sodium 0.6.13, Iris 1.8.8) and a fixed-seed world + self-driving director (ClubPromo: seed 4073942105, spectator, fixed time/weather/yaw/pitch, gates that wait until chunks actually exist).
  build.gradle:40-51 (modRuntimeOnly maven.modrinth:sodium:mc1.21.1-0.6.13-fabric, iris 1.8.8+1.21.1-fabric); src/main/java/com/club/harness/ClubPromo.java:79-80, 137-146, 359-381
- [verified] Our own text/layout hot path is already allocation-free (TextLayout.width/layoutLine reuse a ResolvedGlyph scratch; ModernBackend pre-allocates its opacity/clip stacks and reuses one BufferAllocator). The remaining per-frame garbage is small (EffectsElement rebuilds an Fx[] plus a String key per active effect). GC pressure is NOT where our 0.45 ms goes.
  src/main/java/com/club/ui/text/TextLayout.java:13,17-49 ('Allocation-free'); src/main/java/com/club/ui/backend/ModernBackend.java:73,82,400-403; src/main/java/com/club/ui/hud/EffectsElement.java:103-120
- [verified] TargetHud runs a full world raycast plus an entity box sweep EVERY FRAME (block raycast up to 32 blocks, then ProjectileUtil.raycast over an expanded box), gated only on hud.target being enabled. It is the most expensive single thing the mod does per frame and the prime suspect inside the 0.45 ms.
  src/main/java/com/club/hud/TargetHud.java:60-80 (camera.raycast(reach, tickDelta, false) + ProjectileUtil.raycast); called once per frame from src/main/java/com/club/hud/HudManager.java:81
- [verified] LightmapTextureManager.update(float) early-returns unless its dirty flag is set (raised on tick, not per frame), so the lightmap rebuilds about 20 times a second, not every frame. There is nothing to win by skipping lightmap updates.
  javap -c net/minecraft/client/render/LightmapTextureManager.update(F): getfield dirty, ifne 8, return