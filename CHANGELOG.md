# Changelog

## v0.1.3

**Item Scrolling.** Move items with the mouse instead of clicking them one at a time.

- **Scroll** over a slot to move one item · **Shift** for the whole stack · **Ctrl** for every stack of that
  type · **Ctrl+Shift** for everything in the inventory · **Shift+drag** across slots to move each one you cross.
- Every gesture is **rebindable** — pick the modifier and the button for each action on its own screen. Two
  actions can never share a gesture: assigning one takes it from the other, and the row says so.
- If a gesture overrides something vanilla already does with it (Shift+click is vanilla's own quick-move), the
  row tells you, instead of quietly eating the click.

**Club stops drawing what you cannot see.**

- **Particles behind the camera** are no longer tessellated. Minecraft culls particles not at all — it builds
  the geometry for every live one, every frame, including the ones behind your head. Nothing you can see
  changes: 81% of the particle work simply does not happen in a campfire-heavy scene.
- **Block entities off-screen inside a visible section.** Vanilla frustum-culls the 16×16×16 section but never
  the chest inside it. Anything that asked for unusual treatment — a beacon's beam, an end gateway, a moving
  piston — is never touched.
- Measured, interleaved in one session, on a fixed-seed scene: **−5.3%** frame time on a normal machine,
  **−8.1%** on a CPU-bound one. Where your GPU is the bottleneck, our own benchmark refuses to claim a win —
  and says so.

**The HUD got cheaper to draw.** Its icons each went out through vanilla's immediate path — one GL call per
sprite, and a third of the HUD's cost. They batch now. How many calls that saves is a fact about the scene, so
we do not print one: the harness asserts the batched path issues *fewer* draws than the unbatched one, on every
build. Not a pixel of it looks different, and the harness asserts that by geometry, not by eye.

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
