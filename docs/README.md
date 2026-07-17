# Club — docs

Start with **[ARCHITECTURE.md](ARCHITECTURE.md)**. It is the only one you must read before touching code: it
carries the build (three Minecraft versions, one source tree), the version seams, and the two rules that cost
the most to learn.

## Living documents

These describe how Club works **now**, and are kept true.

| | |
|---|---|
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | How it is built and why. Multiversion, seams, mixins, the checker. **Read first.** |
| [DESIGN.md](DESIGN.md) | The design system: palette, type, spacing. Frozen — flat, dark, no glow. |
| [HUD-LANGUAGE.md](HUD-LANGUAGE.md) | The HUD's visual grammar. Source of truth for any HUD element. |
| [HUDS.md](HUDS.md) | Every HUD element and its settings. |
| [ANIMATIONS.md](ANIMATIONS.md) | Hand positioning and attack animations. |
| [ITEMSCROLL.md](ITEMSCROLL.md) | Item Scroll: gestures, bindings, the server-policy gate. |
| [PERF.md](PERF.md) | What Club culls, how it was measured, and what it refuses to claim. |
| [UI-V2.md](UI-V2.md) · [UI-V2-MENU.md](UI-V2-MENU.md) | The UI stack: render architecture, menu and widget system. |

## Publishing

| | |
|---|---|
| **[MODRINTH.md](MODRINTH.md)** | The store listing, **and the guide for shipping three jars in one update.** |
| [DISCORD.md](DISCORD.md) | Server structure, texts, the release embed. |
| [LAUNCH.md](LAUNCH.md) | Launch posts, for when a page goes live. |

The release itself: `CHANGELOG.md` (its `## vX.Y.Z` section becomes the GitHub release body),
`.github/discord-release.md` (the `@everyone` announcement), `.github/workflows/release.yml` (the machinery).

## Specs and plans

`superpowers/specs/` and `superpowers/plans/` — the design and implementation documents for work in flight.
`2026-07-16-multiversion-design.md` is the current one; **its §4a is the table of measured version
boundaries**, and that table is load-bearing.

## Archive

`archive/` — planning documents for work that is finished. UI V2's stage specs, the Item Scroll and
performance briefs, the render-stack proof of concept.

They are kept because they record **why** a decision went the way it did, and that reasoning is not in the
code. They are NOT kept current: where one disagrees with `ARCHITECTURE.md` or with the source, it is the
archive that is stale. Do not fix them — read them as history.

## Other directories

| | |
|---|---|
| `gallery/` | The Modrinth gallery images. `01-hero.png` is the featured one. |
| `screenshots/` | Screenshots used by the README and the docs. |
| `design/` | Design source material. |
| `bench/`, `research/` | Benchmark output and investigation notes. |
| `icon.png` | The mod icon, used by the README, Modrinth and the Discord embed. |
