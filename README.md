# Club

A clean, **flat** first-person utility client for Minecraft (Fabric 1.21.1). Zoom, fullbright,
freelook, toggle-sprint, custom hands & attack animations, a movable HUD, and per-module hotkeys —
all behind one calm, premium menu. No glass, no glow, no clutter.

Open the menu with **Right Shift**.

![Club menu](docs/screenshots/menu.png)

## Features

**Visuals**
- **Zoom** — hold to magnify the world; smooth eased FOV, scroll to adjust the amount (2×–8×).
- **Fullbright** — see in the dark. Overrides only the world lightmap, so your real brightness slider
  and `options.txt` are never touched.
- **Screen Stretch** — fake a target aspect ratio (with optional letterbox bars).
- **No Hurt Cam / No Fire Overlay / No Bobbing** — a quieter first-person view.

**Player**
- **Hands** — reposition and scale the first-person hands, per hand.
- **Toggle Sprint** — sprint automatically, no key held; with a quiet on-screen indicator.
- **Freelook** — hold to swing the camera around yourself without turning. It never changes your aim
  or movement — a camera feature, not an aim tool.

**Combat**
- **Animations** — a custom first-person attack animation (Overhead / Spin / …), with speed & amplitude.

**HUD** — a movable, editor-driven overlay in one flat "chips" language: status effects, the crosshair
target's health, worn armor, an FPS whisper, and the sprint indicator. Drag to place, right-click for
per-element settings, snap to a grid, or nudge with the arrow keys.

**Everything is keyboard-friendly**, and every toggleable module can be bound to its own **hotkey** from
its settings popover.

## Screenshots

| In-world HUD | Fullbright |
|---|---|
| ![HUD](docs/screenshots/hud.png) | ![Fullbright](docs/screenshots/fullbright.png) |

| Settings popover | HUD editor |
|---|---|
| ![Popover](docs/screenshots/popover.png) | ![Editor](docs/screenshots/editor.png) |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for **Minecraft 1.21.1**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) (1.21.1) and drop it in `mods/`.
3. Drop `club-0.1.0.jar` in your `mods/` folder.
4. Launch. Press **Right Shift** to open the menu.

**Requirements:** Minecraft 1.21.1 · Fabric Loader ≥ 0.15 · Fabric API · Java 21.

## Default controls

| Action | Key |
|---|---|
| Open the Club menu | Right Shift |
| Zoom (hold) | C |
| Freelook (hold) | Left Alt |

Zoom and Freelook are rebindable in vanilla **Options → Controls → Club**. Any module can also be given
a toggle hotkey from its settings popover in the Club menu.

## Build from source

```bash
./gradlew build      # jar → build/libs/club-0.1.0.jar
./gradlew runClient  # launch a dev client
```

Java 21 and an internet connection (for the first dependency fetch) are required.

## License

**All Rights Reserved** — see [LICENSE](LICENSE). You may download and use the official build for your
own gameplay. Copying, re-uploading, mirroring, or redistributing it anywhere is not permitted. The only
official download is the author's Modrinth page.
