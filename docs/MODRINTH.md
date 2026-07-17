# Club — Modrinth listing copy

> Paste the body into the Modrinth project **Description** (Markdown). The summary line goes in the
> **Summary** field. The images in `docs/gallery/` go in the **Gallery**; `01-hero.png` is the featured one.
>
> **Uploading a release is not one upload — it is one per Minecraft version.** See
> [How to publish three versions in one update](#how-to-publish-three-versions-in-one-update) at the bottom.

---

**Summary (short field, 130 chars):**

> Zoom, fullbright, freelook and a HUD you can drag, in one calm menu on Right Shift. Client-side, server-safe — not a cheat client.

---

**Description (Markdown body):**

# Club

A first-person utility client for Fabric — **1.21.1, 1.21.8 and 1.21.11**. Zoom, Fullbright, Freelook, a
movable HUD, hand and animation controls — in one menu, without fighting the game underneath it.

Same mod on every version. Same menu, same modules, same keybinds, same config.

## 🎛️ The menu

**Right Shift.** Flat, calm, dark. No glow, no glass, no cheat-client neon. Every module is a card; right-click
a card for its own settings. The whole menu is keyboard-navigable, and every module can take its own hotkey.

## 👁️ See more

- 🔍 **Zoom** — hold **C** to magnify, with an eased FOV rather than a snap. Scroll while holding to dial it
  from 2× to 8×. Your look sensitivity slows with the zoom, so the world crosses the screen at one speed at any
  magnification — a zoom you can actually aim with.
- 💡 **Fullbright** — full brightness in caves and at night. It only touches the world lightmap, so your real
  Brightness slider and your `options.txt` are never overwritten.
- 🎥 **Freelook** — hold **Left Alt** to swing the camera around yourself without turning. Your aim and your
  movement don't change. It's a camera, not an aim tool.
- 🖼️ **Screen Stretch**, **No Hurt Cam**, **No Fire Overlay**, **No Bobbing** — a calmer view, your way.

## 🏃 Move better

- ⚡ **Toggle Sprint** — sprint without holding the key, with a quiet chip on screen so you always know it's on.
- ✋ **Hands** — reposition and scale the first-person hands, each hand independently.
- ⚔️ **Custom attack animations** — pick a style, then tune its speed and swing.

## ✨ Particles — choose what you see

Every particle the game has, sorted into groups you can reason about: Combat, Blocks, Ambient, Fire & Light,
Water, Explosions and Status.

Pick a group on the left, flip particles one by one on the right — or use the group's own switch to turn all of
it off at once. The search finds a particle across every group at the same time.

**Everything is on by default.** Club does not quietly take particles out of your game; the point is that the
switches exist. Nothing is protected, either — the potion swirls sitting in your face in a fight are yours to
turn off, like the rest of them.

A hidden particle is never created at all: not ticked, not drawn. This is a **visual** choice, not a
performance feature. Turning types off does less work, but there is no number here, because the honest one
depends entirely on what is on your screen at the time.

## 🖱️ Item Scrolling

Move items with the mouse instead of clicking them one at a time.

- **Scroll** over a slot to move one item · **Shift** for the whole stack · **Ctrl** for every stack of that
  type · **Ctrl+Shift** for everything · **Shift+drag** across slots to move each one you cross.
- Every gesture is **rebindable** — pick the modifier and the button for each action, on its own screen. Two
  actions can never share a gesture, and if one of yours overrides something vanilla already does with it, the
  row says so instead of quietly eating the click.

### 🤝 It turns itself off where servers forbid it

Some servers ban item scrolling by rule. Club respects the rule instead of leaving it to you to remember.

Join one of those servers and the feature is simply **not there** — no card in the menu, no hotkey, nothing to
toggle. Leave, and it comes back on its own.

It is one jar. There is no "clean version" to install and no switch to flip, because a switch a player can flip
is a bypass we shipped ourselves. The list of addresses is compiled into the mod, not stored in your config,
for exactly the same reason.

## 📊 The HUD

Status effects, your target's health, worn armour, an FPS readout and the sprint chip, all drawn in one
consistent "chips" language.

Behind it there is a real editor: drag elements, snap to the grid, nudge with the arrow keys, right-click any
element for its own settings. The HUD is the size you set it — Minecraft's GUI Scale doesn't touch it.

## ⚡ It stops drawing what you cannot see

Minecraft renders particles behind your head and block entities that sit off-screen inside a section the
frustum kept. **Club skips those.** Nothing you can see changes.

There is no tab for it and no switch, because neither was ever a choice worth making: they change no pixel.
They are simply on.

Measured on 1.21.1, interleaved in one session, on a fixed-seed scene: **−5.3%** frame time on a normal
machine, **−8.1%** on a CPU-bound one. Where the **GPU** is your bottleneck, our own benchmark refuses to claim
a win — and prints that it refuses. We publish what we measured, in the scene we measured it, on the version we
measured it.

From **1.21.11** Minecraft culls particles itself, and better than we did — so Club stops doing it there. When
the game does the work, we get out of the way.

**What we did not build:** an entity culler. It existed, it measured −22%, and it was deleted — vanilla's
visible-section list only holds sections that contain blocks, so a phantom in open sky belongs to none of them
and would have vanished while you watched it. Want that? Run **Sodium** and **EntityCulling**. We do not
duplicate them, and we will not pretend we could do it better.

## 🧩 Compatibility

**Sodium**, **Iris** (shaderpacks included) and **Freecam** — the gallery images on this page were shot with all
three loaded at once.

Client-side only: Club works on any server and installs on none of them. Java 21, requires **Fabric API**.

**One jar per Minecraft version.** The file says which one it is (`club-0.1.4+mc1.21.8.jar`), and each jar
refuses to load on anything else rather than half-working — a client that starts and then behaves strangely is
worse than one that tells you it is the wrong download.

## ⌨️ Controls

| Action | Default |
|---|---|
| Open the Club menu | **Right Shift** |
| Zoom (hold) | **C** |
| Freelook (hold) | **Left Alt** |

All rebindable, along with a hotkey for every module.

---

## 🛡️ Club is a clean client

- **It never acts for you.** No killaura, no autoclicker, no auto-totem, no combat automation of any kind.
- **It never shows you what a wall hides.** No X-ray, no ESP, no chest or spawner finders, no minimap. The
  Target HUD will not name an entity through a block, and never names one you cannot see at all.
- **It never reaches further than your arm.** No reach, no hitbox.
- **It says nothing to the server your own hand doesn't.**

What Club *does* change is what your own eyes get from what the game already draws: brightness, magnification,
where the camera sits, how your HUD is laid out. Those are conveniences, not secrets — and some servers
regulate them anyway. **Where a server's rules forbid something Club does, Club turns it off there itself**, on
that server, with no switch for you to flip.

### Every module, and what it sends

You asked for this table, so here it is — the whole mod, nothing left out. The right-hand column is the answer
to "is this a cheat", and it says the same word eighteen times. The source is public: check any row of it.

**Visuals**

| Module | What it does | What goes to the server |
|---|---|---|
| **Zoom** | Hold a key to magnify the world, 2×–8×, on a smooth eased FOV. Your look sensitivity slows with the zoom, so the world crosses the screen at one speed at any magnification. | Nothing |
| **Fullbright** | Raises the world lightmap to maximum so you can see in the dark. Your real brightness slider and `options.txt` are never touched. | Nothing |
| **Screen Stretch** | Render at an aspect ratio your monitor doesn't have, with optional letterbox bars. | Nothing |
| **No Hurt Cam** | Removes the red damage screen tilt. | Nothing |
| **No Fire Overlay** | Hides the first-person flames while you burn. | Nothing |
| **No Bobbing** | Stops the view bobbing as you walk. | Nothing |

**Player**

| Module | What it does | What goes to the server |
|---|---|---|
| **Hands** | Reposition and scale the first-person hands, each hand on its own. | Nothing |
| **Toggle Sprint** | Holds your sprint key down for you, so you don't have to. | **Nothing of its own.** The game sends the sprint state it always sends while that key is held — Club only holds the key. |
| **Freelook** | Hold a key to swing the camera around yourself. Your aim, your movement and every packet stay exactly where they were: it is a camera, not an aim tool. | Nothing |

**Combat**

| Module | What it does | What goes to the server |
|---|---|---|
| **Animations** | Replaces the vanilla first-person swing with one of your own, with speed and amplitude. Drawing only — the swing the server sees is vanilla's, at vanilla's timing. | Nothing |

**Particles**

| Module | What it does | What goes to the server |
|---|---|---|
| **Particles** | Every particle the game has, in seven groups, each one switchable. All on by default; nothing is protected. A hidden particle is never created at all — not ticked, not drawn. | Nothing |

**Inventory**

| Module | What it does | What goes to the server |
|---|---|---|
| **Item Scroll** | Move items by scrolling instead of clicking them one at a time. Scroll for one item, **Shift** for the stack, **Ctrl** for every stack of that type, **Shift+drag** across the slots you cross. Every gesture is rebindable. | **Slot clicks — the same ones your own hand sends, just faster.** This is the only module in Club that talks to the server at all. The protocol has no batch move, so a strict anti-cheat may rate-limit a large transfer the way it would rate-limit fast clicking, and roll some of it back. |

**HUD**

| Element | What it does | What goes to the server |
|---|---|---|
| **Target** | Name and health of the living thing under your crosshair, within 4 blocks — a step past vanilla's own attack reach. Never through a block, never one you cannot see, never an armour stand. The range is fixed and is deliberately not a setting. | Nothing |
| **Armor** | Your own armour and its durability. | Nothing |
| **Effects** | Your own potion effects. | Nothing |
| **Info** | Your FPS. | Nothing |
| **Sprint** | Whether Toggle Sprint is currently on. | Nothing |
| **HUD Editor** | Drag, snap, scale and configure every element. | Nothing |

**Misc**

| Module | What it does | What goes to the server |
|---|---|---|
| **Background FPS** | Caps the frame rate while the game sits behind another window. Not in the 1.21.8 and 1.21.11 builds: Minecraft has done this itself since 1.21.2. | Nothing |
| **Hide Effects** | Hides Minecraft's own potion icons. | Nothing |

---

## 💬 Where to find us

- **Source:** [github.com/ClubClient/clubclient](https://github.com/ClubClient/clubclient) — MIT. Every claim on
  this page can be checked against it, including the benchmark that produced the numbers above.
- **Bugs:** [GitHub Issues](https://github.com/ClubClient/clubclient/issues). Bring your mod list and
  `latest.log` — a conflict with the mods you already run is the usual answer.
- **Discord:** [discord.gg/kq2DYuTQnW](https://discord.gg/kq2DYuTQnW) — for getting it set up.

MIT licensed. The interface is set in [Onest](https://github.com/simpals/onest), used under the SIL Open Font
License 1.1.

---
---

# How to publish three versions in one update

**Read this first: Modrinth has no "one upload, three Minecraft versions" for a mod like ours.** It offers one,
and it is a trap — see *Why not one entry* below. Three jars means **three version entries**, created back to
back. It takes about five minutes.

The jars come from the GitHub release the tag builds, or from `versions/*/build/libs/` locally:

```
club-0.1.4+mc1.21.1.jar
club-0.1.4+mc1.21.8.jar
club-0.1.4+mc1.21.11.jar
```

## Do this three times — once per jar

Modrinth → your project → **Versions** → **Create version**.

| Field | 1.21.1 | 1.21.8 | 1.21.11 |
|---|---|---|---|
| **Version number** | `0.1.4+mc1.21.1` | `0.1.4+mc1.21.8` | `0.1.4+mc1.21.11` |
| **Version name** | `Club 0.1.4 — MC 1.21.1` | `Club 0.1.4 — MC 1.21.8` | `Club 0.1.4 — MC 1.21.11` |
| **Loaders** | Fabric | Fabric | Fabric |
| **Game versions** | `1.21.1` **only** | `1.21.8` **only** | `1.21.11` **only** |
| **File** | `club-0.1.4+mc1.21.1.jar` | `club-0.1.4+mc1.21.8.jar` | `club-0.1.4+mc1.21.11.jar` |
| **Release channel** | Release | Release | Release |
| **Changelog** | the same text in all three | ← | ← |

The version number is already inside the jar (`fabric.mod.json` says `0.1.4+mc1.21.8`), so the field and the
artifact agree by construction — nothing to keep in sync by hand.

**Changelog:** paste the `## v0.1.4` section of `docs/CHANGELOG.md`. Same text in all three entries — it is one
release that happens to ship three files, and a player on 1.21.8 should read the same notes as one on 1.21.1.

## Why not one entry with three game versions

Modrinth *will* let you tick 1.21.1, 1.21.8 and 1.21.11 on a single version and attach three files. Do not.

A version has **one primary file**. Launchers, the API and the big green Download button all take that one. A
player on 1.21.8 would be handed the 1.21.1 jar, Fabric would refuse it, and the error would name **our mod** —
so it reads as "Club is broken", not "wrong file". The other two jars would sit there as "additional files",
which most people never open.

Separate entries also make Modrinth's own version filter work: someone browsing on 1.21.11 sees exactly the jar
that runs on 1.21.11, and no others.

## Order, and the one thing that matters

Upload **oldest first** (1.21.1 → 1.21.8 → 1.21.11). Modrinth sorts by publish time, so the newest Minecraft
lands on top of the version list, which is where people look.

Nothing here is destructive and nothing is rushed: you can edit or delete a version entry after publishing,
and the project page updates immediately.

## After Modrinth is live

Tell whoever is holding the tag. **The GitHub release is pushed after Modrinth, never before** — the Discord
announcement fires from the tag and links to Modrinth, so the link has to already work when the ping lands.

The rest is automatic once the tag is pushed:
- `release.yml` builds all three nodes, refuses to publish unless there are as many jars as version nodes,
  attaches them, and pulls the release body out of `docs/CHANGELOG.md`.
- Discord gets an `@everyone` announcement built from `.github/discord-release.md`.
