<img src="docs/icon.png" width="110" align="right" alt="Club icon">

# Club

A clean, **flat** first-person utility client for Minecraft (Fabric **1.21.1**, **1.21.8** and **1.21.11**;
a **1.21.6** build is in development and ships with 0.1.6).
Zoom, fullbright, freelook, item scrolling, a movable HUD, custom hands & attack animations, and per-module
hotkeys — all behind one calm menu. No glass, no glow, no clutter.

Open the menu with **Right Shift**.

[![Modrinth](https://img.shields.io/modrinth/dt/clubclient?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/mod/clubclient)
[![Discord](https://img.shields.io/discord/1526569728850661466?logo=discord&label=Discord&color=5865F2)](https://discord.gg/kq2DYuTQnW)
[![build](https://github.com/ClubClient/clubclient/actions/workflows/build.yml/badge.svg)](https://github.com/ClubClient/clubclient/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

![Club menu](docs/screenshots/menu.png)

## Club is not a cheat client

- **It never acts for you.** No killaura, no autoclicker, no auto-totem, no combat automation of any kind.
- **It never shows you what a wall hides.** No X-ray, no ESP, no chest or spawner finders, no minimap. The
  Target HUD will not name an entity through a block, and never names one you cannot see at all.
- **It never reaches further than your arm.** No reach, no hitbox.
- **It says nothing to the server your own hand doesn't.**

What Club *does* change is what your own eyes get from what the game already draws: brightness, magnification,
where the camera sits, how your HUD is laid out. Those are conveniences, not secrets — and some servers
regulate them anyway. **Where a server's rules forbid something Club does, Club turns it off there itself**, on
that server, with no switch for you to flip.

The source is right here; you don't have to take our word for any of it. The table below is the whole mod, and
the right-hand column is the answer to "is this a cheat".

## Features

This is the whole mod. Nothing is left out of this table, and the right-hand column is the answer to "is this
a cheat" — it says the same word eighteen times.

### Visuals

| Module | What it does | What goes to the server |
|---|---|---|
| **Zoom** | Hold a key to magnify the world, 2×–8×, on a smooth eased FOV. Your look sensitivity slows with the zoom, so the world crosses the screen at one speed at any magnification. | Nothing |
| **Fullbright** | Raises the world lightmap to maximum so you can see in the dark. Your real brightness slider and `options.txt` are never touched. | Nothing |
| **Screen Stretch** | Render at an aspect ratio your monitor doesn't have, with optional letterbox bars. | Nothing |
| **No Hurt Cam** | Removes the red damage screen tilt. | Nothing |
| **No Fire Overlay** | Hides the first-person flames while you burn. | Nothing |
| **No Bobbing** | Stops the view bobbing as you walk. | Nothing |

### Player

| Module | What it does | What goes to the server |
|---|---|---|
| **Hands** | Reposition and scale the first-person hands, each hand on its own. | Nothing |
| **Toggle Sprint** | Holds your sprint key down for you, so you don't have to. | **Nothing of its own.** The game sends the sprint state it always sends while that key is held — Club only holds the key. |
| **Freelook** | Hold a key to swing the camera around yourself. Your aim, your movement and every packet stay exactly where they were: it is a camera, not an aim tool. | Nothing |

### Combat

| Module | What it does | What goes to the server |
|---|---|---|
| **Animations** | Replaces the vanilla first-person swing with one of your own (Overhead / Spin / …), with speed and amplitude. Drawing only — the swing the server sees is vanilla's, at vanilla's timing. | Nothing |

### Particles

| Module | What it does | What goes to the server |
|---|---|---|
| **Particles** | Every particle the game has, in seven groups, each one switchable. All on by default; nothing is protected. A hidden particle is never created at all — not ticked, not drawn. | Nothing |

### Inventory

| Module | What it does | What goes to the server |
|---|---|---|
| **Item Scroll** | Move items by scrolling instead of clicking them one at a time. Scroll for one item, **Shift** for the stack, **Ctrl** for every stack of that type, **Shift+drag** across the slots you cross. Every gesture is rebindable. | **Slot clicks — the same ones your own hand sends, just faster.** This is the only module in Club that talks to the server at all. The protocol has no batch move, so a strict anti-cheat may rate-limit a large transfer the way it would rate-limit fast clicking. |

### HUD

| Element | What it does | What goes to the server |
|---|---|---|
| **Target** | Name and health of the living thing under your crosshair, within 4 blocks — a step past vanilla's own attack reach. Never through a block, never one you cannot see, never an armour stand. The range is fixed and is deliberately not a setting. | Nothing |
| **Armor** | Your own armour and its durability. | Nothing |
| **Effects** | Your own potion effects. | Nothing |
| **Info** | Your FPS. | Nothing |
| **Sprint** | Whether Toggle Sprint is currently on. | Nothing |
| **HUD Editor** | Drag, snap, scale and configure every element. The HUD is the size you set it — Minecraft's GUI Scale doesn't touch it. | Nothing |

### Misc

| Module | What it does | What goes to the server |
|---|---|---|
| **Background FPS** | Caps the frame rate while the game sits behind another window — your battery and your fans, zero in-game cost. Only in the 1.21.1 build: Minecraft has done this itself since 1.21.2, so it is absent from 1.21.6, 1.21.8 and 1.21.11. | Nothing |
| **Hide Effects** | Hides Minecraft's own potion icons. | Nothing |

### Where Club stands down

Some servers forbid some of this by rule, and Club keeps the rule for you rather than leaving it to you to
remember:

| Server | What is absent there |
|---|---|
| **Astrum** | Item Scroll, Freelook |
| **Aormio** | Item Scroll |

No card in the menu, no hotkey, nothing to toggle — on those servers this client simply *is* a build without
the module. It is one jar, and there is no switch, because a switch you could flip is a bypass we shipped
ourselves.

### Performance

Club skips drawing particles behind the camera, and block entities off-screen inside a section the game
already decided was visible. Both are always on and baked in: they never change a pixel, so they were never
dials worth tuning. On **1.21.11 the particle cull is gone** — the game does it itself now, and by a better
test than ours. On **1.21.1, 1.21.6 and 1.21.8 the particle cull is still ours**: vanilla only took the job
over at 1.21.11, and 1.21.6 is on the near side of that line.

Measured **on 1.21.1**, interleaved in one session on a fixed-seed scene: **−5.3%** frame time on a normal
machine, **−8.1%** on a CPU-bound one. Where the **GPU** is your bottleneck our own benchmark refuses to claim
a win, and prints that it refuses. Those numbers belong to that version and that scene and nowhere else — the
1.21.6, 1.21.8 and 1.21.11 builds have never been benched, and the 1.21.11 build no longer contains half of
what was being measured. Run it yourself: `CLUB_BENCH=1 ./gradlew runClient`, report in [docs/bench/](docs/bench/).

**Everything is keyboard-friendly**, and every toggleable module can be bound to its own **hotkey** from its
settings popover.

## Screenshots

| In-world HUD | Fullbright |
|---|---|
| ![HUD](docs/screenshots/hud.png) | ![Fullbright](docs/screenshots/fullbright.png) |

| Settings popover | HUD editor |
|---|---|
| ![Popover](docs/screenshots/popover.png) | ![Editor](docs/screenshots/editor.png) |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for **1.21.1**, **1.21.8** or **1.21.11**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for that same version and drop it in `mods/`.
3. Get Club from [Modrinth](https://modrinth.com/mod/clubclient) or
   [Releases](https://github.com/ClubClient/clubclient/releases) — **one jar per Minecraft version**, named
   for the one it is built against (`club-0.1.5+mc1.21.8.jar`). Drop it in `mods/`.
4. Launch. Press **Right Shift** to open the menu.

Each jar declares the single Minecraft version it was built for and will not load on another. That is
deliberate: a client that starts and then behaves strangely is worse than one that says you have the wrong
download.

A **1.21.6** jar is being built alongside these and is not published yet; it arrives with 0.1.6.

**1.21.7, 1.21.9 and 1.21.10 are not covered, and that is a decision rather than a gap.** Their mappings and
Fabric API coordinates have never been measured here, and this project does not declare a version range it has
not built against — a jar that loads and then misbehaves is worse than one that is honestly absent.

**Requirements:** Minecraft 1.21.1 / 1.21.8 / 1.21.11 (1.21.6 from 0.1.6) · Fabric Loader ≥ 0.15 · Fabric API · Java 21.
**Compatible with:** Sodium, Iris (shaderpacks included), Freecam — the gallery images were shot with all
three loaded at once.

## Default controls

| Action | Key |
|---|---|
| Open the Club menu | Right Shift |
| Zoom (hold) | C |
| Freelook (hold) | Left Alt |

Zoom and Freelook are rebindable in vanilla **Options → Controls → Club**. Any module can also be given a
toggle hotkey from its settings popover in the Club menu.

## Build from source

```bash
./gradlew build      # jar → build/libs/club-<version>.jar
./gradlew runClient  # launch a dev client
```

Java 21 and an internet connection (for the first dependency fetch) are required.

Club ships its own instruments, and they are not tests — they are separate programs that drive a real client:

```bash
CLUB_HARNESS=1 ./gradlew ":1.21.6:runClient" --args="--quickPlaySingleplayer club-harness-world"  # 127/0, 35 shots
CLUB_BENCH=1 ./gradlew runClient                                                        # ON/OFF interleaved, paired deltas
./gradlew runClient -PclubCompat -PclubIris                                             # Sodium + Iris + Freecam
```

Name the version node (`:1.21.6:runClient`), do not run the bare task: with four Stonecutter nodes an unscoped
`runClient` starts a client for every one of them at once, and they fight over the window focus — which reads as
a broken feature rather than as a broken way of measuring. Each node also needs its own
`versions/<v>/run/saves/club-harness-world`, or the client waits on the title screen forever for a world that is
never loaded. Seed it from a LOWER version: Minecraft upgrades a world silently but stops on a modal dialog when
asked to downgrade one, and a harness parked behind a dialog looks exactly like a harness that hung.

The counts above are one measured run, not a constant: 1.21.6 asserted 127/0 and 1.21.8 126/0 on 2026-07-22, both
with 35 screenshots. They differ because the harness only asserts what a version actually ships. Re-measure before
quoting either.

See [CONTRIBUTING.md](.github/CONTRIBUTING.md) before opening a pull request — there are six rules that will close
one, and each was bought with a bug.

## Help & bugs

- **A bug** → [open an issue](https://github.com/ClubClient/clubclient/issues/new/choose). Bring your mod list
  and `latest.log`; a conflict with the mods you already run is the usual answer.
- **Getting it set up** → [Discord](https://discord.gg/kq2DYuTQnW), `#support`.

## License

**MIT** — see [LICENSE](LICENSE).

The UI is set in [Onest](https://github.com/simpals/onest) by Dmitri Voloshin and Andrey Kudryavtsev, used
under the [SIL Open Font License 1.1](https://scripts.sil.org/OFL) — see
[THIRD-PARTY-NOTICES.md](docs/THIRD-PARTY-NOTICES.md). The font files live in `tools/fonts/`; the jar carries a
pre-rendered MSDF atlas of them. Every icon is drawn for Club.
