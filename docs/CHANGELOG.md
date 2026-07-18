# Changelog

## v0.1.5

**See inside a shulker box without opening it.** Hover one and its contents show as a little chest-slot grid —
the real items, with their counts, sitting in real inventory slots. Just what's in the box: no name, no text
list, no clutter. Prefer Minecraft's plain version? Turn Shulker Tooltip off in Misc. (Asked for on Reddit.)

**Small Totem.** The totem-of-undying pop stops filling the middle of the screen — it is smaller now and lifted
up out of the way, so the pop that just saved you no longer blocks the fight it saved you from. Baked in,
always on, no card.

**Low Shield.** Raise a shield to block and it drops low instead of covering half your view. Baked in, always
on, no card.

**A slimmer, quieter HUD.** The Target chip is about a sixth smaller — the same HP, name and health bar, just
less bulk in your face. The sprint pill is a touch more transparent, the FPS readout a touch brighter. And
hiding Minecraft's own potion icons behind Club's Effects chip is automatic now: no toggle to find, it just
happens while the Club chip is on, and the game's icons come back if you turn the chip off.

**Freelook turns itself off on Astrum.** Astrum's rules ban Perspective Mod — the mod whose whole job is letting
you look around while your aim and your movement keep pointing where they were. That is exactly what Club's
Freelook does, so the name it ships under changes nothing: on Astrum it is a banned mod, and Club stops handing
it to you there.

- Join Astrum and Freelook is simply not there — no card in the menu, and the key does nothing. Leave, and it
  comes back on its own.
- **On Aormio it stays.** Their rules ban automation and forged packets — Killaura, AutoTotem, AimAssist — and
  Freelook is none of that. A rule belongs to the server that wrote it, and we are not going to invent Aormio's
  rules for them.
- Same shape as Item Scroll, for the same reason: one jar, no switch. A switch a player can flip is a bypass we
  shipped ourselves.

**This should have been caught a long time ago.** Freelook has been in Club since v0.1, and Astrum's list has
named Perspective Mod the whole time. The mistake was in how the list was read: we looked for our own name, and
our name is never going to be on it. A ban list names *functions*, and the only question that matters is what a
moderator sees when they watch you play.

**Every module, and what it sends — in one table.** Asked for on Reddit, and now on the Modrinth page and in
the README: every module, what each one does, and what each one sends to the server. The right-hand column says
**Nothing** for all but two rows, and names those two — Item Scroll sends slot clicks (the same ones your own
hand sends), and Toggle Sprint holds a vanilla key down so the game sends what it always would.

**The promise above that table is new, because the old one was not true.** It said "nothing in Club gives you
information the game doesn't". Fullbright hands you brightness 15.0 where the game's own slider stops at 1.0 —
a dark cave shows you its contents, and that is information the game does not give. The sentence had been on
the page since the first release, and it was the same sentence we used to rule out building a minimap. One
standard or none.

What stands there now is narrower, and every line can be checked against the source you already have: Club
never acts for you, never shows you what a wall hides, never reaches further than your arm, and never says
anything to the server your own hand doesn't. What it *does* change is what your own eyes get from what the
game already draws — and where a server forbids even that, Club turns it off there itself.

## v0.1.4

**Club runs on 1.21.8 and 1.21.11 now, as well as 1.21.1.** Three jars, one for each — pick the one that
matches your game. Same modules, same keybinds, same HUD, same menu, same config.

The Minecraft version is in the file name and in the mod's own version string (`club-0.1.4+mc1.21.8.jar`), and
each jar refuses to load on anything else rather than half-working: a client that starts and then behaves
strangely is worse than one that tells you it is the wrong download.

**Item Scroll turns itself off on servers that forbid it.** Astrum and Aormio ban item scrolling by rule, and
Club respects the rule instead of leaving it to you to remember.

- Join one of those servers and the feature is simply not there — no card in the menu, no hotkey, nothing to
  toggle. Leave, and it comes back on its own.
- It is one jar. There is no "clean version" to install and no switch to flip, because a switch a player can
  flip is a bypass we shipped ourselves.
- The list of addresses is compiled into the mod, not stored in your config, for the same reason. Both of that
  server's doors are covered, including the ones we found rather than were given.

**Particles — choose what you see.** A new category holding every particle the game has, sorted into groups you
can reason about: Combat, Blocks, Ambient, Fire & Light, Water, Explosions and Status.

- Pick a group on the left, flip particles one by one on the right — or use the group's own switch to turn all
  of it off at once. The search finds a particle across every group at the same time.
- **Everything is on by default.** Club does not quietly take particles out of your game; the point is that the
  switches exist. Nothing is protected, either — the potion swirls sitting in your face in a fight are yours to
  turn off, like the rest of them.
- A hidden particle is never created at all: not ticked, not drawn. This is a **visual** choice, not a
  performance feature — it is about what you want to look at. Turning types off does less work, but there is no
  number here, because the honest one depends entirely on what is on your screen at the time.

**The Performance tab is gone, and the work it did simply happens.** The two culls it held — particles behind
the camera, block entities off-screen inside a visible section — never changed a pixel, so they were never
dials worth tuning: they are baked in and always on. **Background FPS** was the one setting there that is a
real choice (it caps the frame rate only while the game sits behind another window — your battery and your
fans, zero in-game FPS), so it kept its cap and moved to Misc.

If you ever need to rule Club out of a suspected rendering bug, those culls still have an off switch — it lives
in `config/club_settings.json` (`perf.cullParticles`, `perf.cullBlockEntities`), instead of a menu toggle
pretending to be a quality setting.

**Two things Minecraft now does itself, so Club stopped doing them.** Where the game does the work, we get out
of the way rather than doing it twice:

- **Background FPS is not in the 1.21.8 and 1.21.11 builds.** Minecraft caps its own frame rate behind another
  window from 1.21.2 on. Your setting is Minecraft's now, not ours.
- **From 1.21.11 the game culls particles behind you**, and by a better test than ours — we only ever skipped
  what sat behind the camera, and it drops everything outside your view. So Club's particle cull is not in that
  build. Nothing changes for you; the work still doesn't happen.

## v0.1.3

**Item Scrolling (beta).** Move items with the mouse instead of clicking them one slot at a time.

- **Scroll** a slot to move one item · **Shift** the whole stack · **Ctrl** every stack of that type ·
  **Ctrl+Shift** the whole inventory · **Shift+drag** across slots to move each one you cross.
- Every control is **rebindable** — choose the modifier and the button for each action on its own screen. No
  two actions can share one: assign it to a second and it leaves the first, and the row tells you.
- When a control would override something vanilla already does with it (Shift+click is the game's own
  quick-move), the row says so, instead of quietly swallowing the click.
- It ships as **beta** while it settles: it doesn't reach the creative inventory yet, and the card admits that
  rather than pretending to work.

**Settings that belong to their card.** Right-click a module and its settings unfold directly under that card,
sliding the cards below it down to make room — the sheet never covers the grid. It is the width of its card,
its controls squeeze to fit, and a name too long to fit is shortened rather than spilled. A module with nothing
to configure no longer opens an empty panel — it flashes a small crossed-out gear and leaves the card in place.

**Club stops drawing what you cannot see.**

- **Particles behind the camera** are no longer tessellated. Minecraft culls particles not at all — it builds
  the geometry for every live one, every frame, including the ones behind your head. Nothing you can see changes.
- **Block entities off-screen inside a visible section.** Vanilla frustum-culls the 16×16×16 section but never
  the chest inside it. Anything that asked for unusual treatment — a beacon's beam, an end gateway, a moving
  piston — is never touched.
- Measured, interleaved in one session, on a fixed-seed scene: **−5.3%** frame time on a normal machine,
  **−8.1%** on a CPU-bound one. Where your GPU is the bottleneck, our own benchmark refuses to claim a win —
  and says so.

**A cheaper, tidier HUD.** The HUD's icons each went out through vanilla's immediate path — one GL call per
sprite, and a third of the HUD's cost. They batch now, and every build proves *fewer* draws than the unbatched
path by geometry, not by eye — not a pixel looks different. In the editor, elements refuse to overlap: drag one
onto another and it slides along the edge instead of stacking, so nothing hides under a neighbour or under the
toolbar. The target chip's reach is fixed one step past vanilla's — a dial for it would name an opponent before
you could touch them, and that is a soft cheat we won't ship.

**Background throttle.** Cap the frame rate while the window is behind something else. This gives **zero
in-game FPS** — it is a battery, fan-noise and second-monitor feature, and calling it an FPS boost would be a
lie. It can never raise a limit you chose yourself.

*What we did not ship:* an entity culler. It was built, measured at −22% frame time, and deleted — vanilla's
visible-section list only contains sections that hold blocks, so a phantom in open sky belongs to none of them
and would have vanished while you watched it. If you want that, run **Sodium** (it owns the occlusion graph) and
**EntityCulling** on top. We do not duplicate them.

## v0.1.2

**Club and Minecraft stopped fighting over your keys.**

Minecraft hands a key press to exactly one binding. So a Club key that landed on a key the game already used
did not share it — one of the two actions silently stopped working, and which one lost depended on your mod
list. Bind Freelook to Left Shift and you quietly lost the ability to sneak, with nothing anywhere saying why.

- Club now reads the game's own bindings and **names the conflict** in the module's popover, the way vanilla's
  Controls screen paints duplicates red.
- Rebinding Zoom or Freelook in the Club menu changes it in **Options → Controls → Club**, and the other way
  round. It is one binding, not two settings that drift apart.
- **The menu can no longer be locked away from you.** Vanilla's Controls screen lists all three Club binds side
  by side, and it was possible to take the menu's own key — leaving the bind fixable only from the menu that
  would no longer open.
- A module key that a hold module (Zoom, Freelook) has taken over now says so, instead of sitting there looking
  like it still works.

**Toggle Sprint could latch the sprint key down forever.** If you switched vanilla's "Sprint: Toggle" on while
the module was running, its release did nothing and you sprinted for the rest of the session. Fixed.

**The HUD is the size you set it.**

It used to be laid out in Minecraft's GUI-scale units, so a video setting quietly resized every element — on
top of that element's own Size slider, which meant "1.0" was a different physical size on every machine. The
HUD now has its own canvas, like the menu:

| GUI Scale | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| Chip width, real pixels | 90 | 90 | 90 | 90 |
| Before this release | 45 | 90 | 135 | 180 |

The HUD editor moved with it, so what you drag is the size you get. Saved positions are converted, not
reinterpreted — nothing jumps across the screen.

**The menu stopped lying.**

- Screen Stretch on **Auto**, and Toggle Sprint while vanilla's own sprint toggle is on, are deliberate no-ops.
  They used to sit there lit like a working module. They now say why they are idle.
- Turning off Club's Effects HUD left you with **no** effect display at all — not ours, and not the game's. A
  replacement only hides the original while it is on screen.
- Screen Stretch's letterbox is drawn again on F1 and behind the pause menu. It masks a world we deliberately
  over-render; hiding the HUD must not un-mask it.
- A movement key held while walking into the HUD editor and back no longer goes dead.

**Licence.** Club is MIT now. Source to GitHub shortly.

---

## v0.1.1

- Zoom's hold key is a real hold key: it no longer collides with a module's toggle key, which is why zoom used
  to fire on every other press. Your look also slows while zoomed, so the world crosses the screen at one speed
  at any magnification.
- The menu draws on its own canvas — at GUI Scale 4 it used to hit the edges of the screen, drop columns and
  squeeze its sidebar.
- The mod's draw cost: every shape is batched into one GL call now, every glyph into another.
- Screen Stretch ships as **Auto** — a fresh install no longer warps the world of anyone whose monitor isn't
  16:9.
- Fullbright only touches the world lightmap, so your real Brightness slider and `options.txt` stay honest.

## v0.1.0

First release: Zoom, Fullbright, Freelook, Toggle Sprint, Screen Stretch, No Hurt Cam, No Fire Overlay,
No Bobbing, custom hands, custom attack animations, a movable HUD with its own editor, and per-module hotkeys.
