# Club — Modrinth listing copy

> Paste the body into the Modrinth project **Description** (Markdown). The summary line goes in the
> **Summary** field. The six images in `docs/gallery/` go in the **Gallery**; `01-hero.png` is the featured
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

## 📊 The HUD

Status effects, your target's health, worn armour, an FPS readout and the sprint chip, all drawn in one
consistent "chips" language.

Behind it there is a real editor: drag elements, snap to the grid, nudge with the arrow keys, right-click any
element for its own settings. The HUD is the size you set it — Minecraft's GUI Scale doesn't touch it.

---

## ✨ New in v0.1.2

**Club and Minecraft no longer fight over your keys.** Minecraft hands a key press to exactly one binding, so a
Club key that landed on a key the game already used didn't share it — one of the two actions silently stopped
working, and which one lost depended on your mod list. Club now reads the game's own bindings and *names* the
conflict in the popover, the way vanilla's Controls screen paints duplicates red. Rebind Zoom or Freelook in
the Club menu and it changes in **Options → Controls → Club**, and the other way round. It's one binding, not
two settings that drift apart.

**The menu can no longer be locked away from you.** Vanilla's Controls screen could take the menu's own key,
leaving the bind fixable only from the menu that would no longer open.

**The HUD is the size you set it.** It used to be laid out in Minecraft's GUI-scale units, so a video setting
resized every element — on top of that element's own Size slider. It has its own canvas now: the same physical
size at GUI Scale 1, 2, 3 and 4.

| GUI Scale | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| Measured size | 90px | 90px | 90px | 90px |

It used to be 1:2:3:4. Saved positions are migrated, not reinterpreted, so you don't have to lay the HUD out
again.

**The menu stopped lying.** A module that's switched on but standing down — Screen Stretch on Auto, Toggle
Sprint while vanilla's own sprint toggle is on — now says so, instead of sitting there lit as though it were
doing something.

**Toggle Sprint** could latch the sprint key down forever if you turned on vanilla's "Sprint: Toggle" while it
was running. Fixed. And turning off Club's Effects HUD no longer leaves you with no effect display at all.

## ⚡ It measures its own cost

The in-world HUD draw is **11 GL calls a frame**, down from 43, at roughly **0.45 ms**. That's measured in-game
on every build and asserted, so it fails its own test if it creeps back.

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
X-ray. Nothing in it touches what the server sees.

Everything Club does is about *your* view of the game and *your* convenience at the keyboard.

So use it on servers that ban hacks. There is nothing in it to ban.

---

MIT licensed. Source on GitHub shortly.
