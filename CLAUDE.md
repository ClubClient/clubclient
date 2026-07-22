# Club Development

This file codifies the essential practices and commands for Club development. See `docs/` for architecture, feature details, and release flow.

## Build

Club uses **Stonecutter** for multi-version support. Build the four MC versions via:

```bash
./gradlew ":1.21.8:build"
./gradlew ":1.21.1:build"
./gradlew ":1.21.6:build"
./gradlew ":1.21.11:build"
```

Version nodes live in `versions/<v>/`. Each build produces a jar in the version's `build/libs/` folder.

## Instruments (testing harnesses)

Run on a **large window** (not 854×480 — small windows miss scaling bugs).

- **ClubHarness** (`CLUB_HARNESS=1`): main HUD and feature harness. Screenshots → `run/screenshots/`.
- **ClubBench** (`CLUB_BENCH=1`): performance bench. Perf numbers must be reproducible — re-measure before any release; never publish a number the bench won't repeat.
- **ClubPromo** (`CLUB_PROMO=1`): promotional / gallery screenshots (see `docs/gallery/`).

**Launch (REQUIRED):** the harness needs a quick-play world, or the client hangs on the title screen (it waits for `mc.world`):

```bash
CLUB_HARNESS=1 ./gradlew runClient --args="--quickPlaySingleplayer club-harness-world"
```

## Workflow: Code → Jar → Desktop

After any code change:
1. Rebuild: `./gradlew ":1.21.8:build"` (+ other versions if needed).
2. Copy the fresh jar to `Desktop/CLUB/<version>/`.

This keeps the launch folder always up-to-date so testing sees your changes immediately.

## Design (frozen)

- **Flat, dark.** Neutral palette + flat accent (#7CABFF).
- **No glow, glass, or text gradients.**
- See `docs/README.md` → design section for reference.

## Honesty (non-negotiable)

- Never claim "done" without proof: instruments, build output, or test run.
- **Fix instruments first.** A broken harness masks real bugs.
- Never print a number your instrument won't reproduce.

## Navigation

Start here:
- `docs/README.md` — index; read **`docs/ARCHITECTURE.md` first** before any refactor or new module.
- `docs/CHANGELOG.md` — features, versions, known issues.
- `.github/workflows/release.yml` — release automation (every version node, Modrinth, GitHub).

## See also

- Project memory: `~/.claude/projects/c--Club-Club/memory/` (system decisions, traps, release state).
- This mod is multi-version: read `ARCHITECTURE.md` carefully before changing render/mixins.
