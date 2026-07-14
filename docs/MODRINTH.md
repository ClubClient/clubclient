# Club — Modrinth listing copy

> Paste the body into the Modrinth project **Description** (Markdown). The summary line goes in the
> **Summary** field. The seven images in `docs/gallery/` go in the **Gallery**; `01-hero.png` is the featured
> one. The changelog for the version upload is `CHANGELOG.md`.

---

**Summary (short field, 130 chars):**

> Zoom, fullbright, freelook and a HUD you can drag, in one calm menu on Right Shift. Client-side, server-safe — not a cheat client.

---

**Description (Markdown body):**

# Club

A first-person utility client for Fabric 1.21.1. Zoom, Fullbright, Freelook, a movable HUD, hand and animation
controls — in one menu, without fighting the game underneath it.

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

## 🖱️ Item Scrolling

Move items with the mouse instead of clicking them one at a time.

- **Scroll** over a slot to move one item · **Shift** for the whole stack · **Ctrl** for every stack of that
  type · **Ctrl+Shift** for everything · **Shift+drag** across slots to move each one you cross.
- Every gesture is **rebindable** — pick the modifier and the button for each action, on its own screen. Two
  actions can never share a gesture, and if one of yours overrides something vanilla already does with it, the
  row says so instead of quietly eating the click.

## 📊 The HUD

Status effects, your target's health, worn armour, an FPS readout and the sprint chip, all drawn in one
consistent "chips" language.

Behind it there is a real editor: drag elements, snap to the grid, nudge with the arrow keys, right-click any
element for its own settings. The HUD is the size you set it — Minecraft's GUI Scale doesn't touch it.

---

## ✨ New in v0.1.3

**Item Scrolling.** Move items with the mouse instead of clicking them one at a time — scroll a slot for one
item, Shift for the stack, Ctrl for every stack of that type, Shift+drag across slots to move each one you
cross. Every gesture is rebindable, two actions can never share one, and if a gesture overrides something
vanilla already does with it, the row says so instead of quietly eating the click. (See the section above.)

**Club stops drawing what you cannot see.** Particles behind the camera are no longer tessellated; block
entities that sit off-screen inside a section the frustum kept are no longer rendered. Measured, interleaved in
one session: **−5.3%** frame time on a normal machine, **−8.1%** on a CPU-bound one. Where your GPU is the
bottleneck, our own benchmark refuses to claim a win — and says so.

**Background throttle.** Cap the frame rate while the window is behind something else. This gives **zero
in-game FPS** — it is a battery and fan-noise feature, and calling it an FPS boost would be a lie.

**The HUD's icons batch now**, instead of each going out through vanilla's immediate path.

*Previously, in v0.1.2:* Club and Minecraft stopped fighting over your keys (conflicts are now *named* in the
popover, and one bind is one bind — the Club menu and **Options → Controls** are the same setting); the menu
can no longer be locked away from you; the HUD got its own canvas, so GUI Scale no longer resizes it; and a
module that is switched on but standing down now says so instead of sitting there lit.

## ⚡ It stops drawing what you cannot see

Minecraft does not cull particles at all — it builds the geometry for every live one, every frame, including
the ones behind your head. **Club skips those.** Nothing you can see changes: in a campfire-heavy scene, 81% of
the particle work simply does not happen. Same for block entities that sit off-screen inside a section the
frustum kept — vanilla culls the 16×16×16 box, never the chest inside it.

Measured, interleaved in one session, on a fixed-seed scene:

| | frame time |
|---|---|
| A normal machine | **−5.3%** |
| A CPU-bound machine | **−8.1%** |

Where the **GPU** is your bottleneck, our own benchmark refuses to claim a win — and prints that it refuses.
We publish what we measured, in the scene we measured it.

And the mod pays its own way: the HUD's icons used to go out through vanilla's immediate path, one GL call per
sprite. They batch now. Our harness asserts that the batched path issues **fewer** draws than the unbatched one
on every build — a property, not a number, because the number is a fact about the scene it was measured in.

**What we did not build:** an entity culler. It existed, it measured −22%, and it was deleted — vanilla's
visible-section list only holds sections that contain blocks, so a phantom in open sky belongs to none of them
and would have vanished while you watched it. Want that? Run **Sodium** and **EntityCulling**. We do not
duplicate them, and we will not pretend we could do it better.

## 🔋 Background throttle

Cap the frame rate while the window is behind something else. This gives **zero in-game FPS** — it is a
battery, fan-noise and second-monitor feature, and calling it an FPS boost would be a lie. It can never raise a
limit you chose yourself.

## 🧩 Compatibility

**Sodium**, **Iris** (shaderpacks included) and **Freecam** — the gallery images on this page were shot with all
three loaded at once.

Client-side only: Club works on any server and installs on none of them. Fabric 1.21.1, Java 21. Requires
**Fabric API**.

## ⌨️ Controls

| Action | Default |
|---|---|
| Open the Club menu | **Right Shift** |
| Zoom (hold) | **C** |
| Freelook (hold) | **Left Alt** |

All rebindable, along with a hotkey for every module.

---

## 🛡️ Club is a clean client

Club is not a cheat client. No combat automation, no killaura, no player ESP, no reach, no autoclicker, no
X-ray. Nothing in it gives you information the game does not, or reach the game does not.

Everything Club does is about *your* view of the game and *your* convenience at the keyboard.

One honest footnote, because the source is public and you can check it: **Item Scrolling moves items by
clicking slots** — the same packets your own hand sends, just faster. That is all it can do; the protocol has
no batch move. A strict anti-cheat may rate-limit a large transfer the way it would rate-limit fast clicking,
and roll some of it back. Nothing else in Club talks to the server at all.

---

MIT licensed. Source on GitHub shortly.
