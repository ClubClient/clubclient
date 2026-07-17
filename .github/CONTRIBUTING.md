# Contributing to Club

Thanks for looking. This page is short on purpose — it is the set of rules that will get a pull request
closed if you don't know them, and nothing else.

## Build

```bash
./gradlew build        # jar -> build/libs/club-<version>.jar
./gradlew runClient    # dev client
```

Java 21, and an internet connection for the first dependency fetch. That's it.

Most of the documentation is in **Russian** (`docs/`) — this project was written in Russian and the docs were
never a translation exercise. **Code, code comments and every UI string are English**, and must stay that way.
Start at [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## The rules that close pull requests

These are not style preferences. Each one is here because breaking it cost this project something.

**1. No performance number without the instrument's output pasted in the PR.**
Club has retracted a false performance figure **three times**. It now ships its own benchmark:

```bash
CLUB_BENCH=1 ./gradlew runClient
```

It interleaves ON/OFF in one session, takes paired differences, prints its own noise floor, and refuses to
name an effect when the pairs disagree on the sign. If you claim a speedup, paste what it printed — including
the noise floor. If the bench declined to certify, say that instead. Do not compare two separate runs; the
bench will tell you why.

**2. Verify in the game, not "it should work".**
There is an acceptance harness — a self-driving client that runs 94 checks and takes 25 screenshots:

```bash
CLUB_HARNESS=1 ./gradlew runClient --args="--quickPlaySingleplayer club-harness-world"
```

It must be green. If you touched rendering, run it under the mods that rewrite rendering too:

```bash
./gradlew runClient -PclubCompat              # Sodium + Freecam
./gradlew runClient -PclubCompat -PclubIris   # ...and the shader pipeline
```

**3. The visual design is frozen.** Flat. No glass, no glow, no gradient on text — the single exception is the
underline of the active tab. The palette is in [docs/DESIGN.md](../docs/DESIGN.md). **The menu and its popovers
are frozen too**, by the owner's decision. A visual-redesign PR is closed; this is not a judgement of your
taste, it is simply not open.

**4. Club is not a cheat client.** No killaura, no ESP, no reach, no autoclicker, no X-ray, no combat
automation. Nothing that gives an advantage the server can see. This is the entire positioning of the mod.
PRs adding these are closed on sight.

**5. Mixins: `@WrapOperation`, not `@Redirect`.**
`club.mixins.json` sets `"required": true`. Two `@Redirect`s on one instruction is an **exclusive claim** — the
loser's mixin fails to apply and *the game refuses to launch*. Every popular hook (particles, the camera, the
mouse) is exactly where another mod is already sitting. MixinExtras ships with Fabric Loader; use it. See
`MixinMouse.java:29` and `MixinParticleManager.java` for the established idiom.

Prefer `@Accessor`/`@Invoker` over injecting at all — Item Scroll is built on **zero** `@Inject`, which is why
it has nothing to fight Sodium, REI or EMI over.

**6. A ban at the door is not a ban in the act.**
If a value must never be used, the code that *uses* it has to refuse — not only the screen that *sets* it.
`config.json` is a text file; people edit text files. This rule was bought with a bug, twice.

## Commits

One commit per coherent change. The message explains **why**, not what — `git log` in this repo is a record of
reasoning, and it is worth reading.

## Third-party assets

The interface is set in [Onest](https://github.com/simpals/onest) under the SIL Open Font License — see
[THIRD-PARTY-NOTICES.md](../docs/THIRD-PARTY-NOTICES.md). The font files are named `inter_*.ttf` for historical
reasons; they contain Onest. Every icon is drawn for Club (`tools/icons/src/`).

## Licence

MIT. By contributing you agree your work ships under it.
