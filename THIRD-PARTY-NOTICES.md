# Third-party notices

Club is MIT licensed (see [LICENSE](LICENSE)). It builds on the work below, which carries its own terms.

## Onest — SIL Open Font License 1.1

Copyright 2021 The Onest Project Authors (https://github.com/googlefonts/onest)
Dmitri Voloshin, Andrey Kudryavtsev · https://onest.md

Every glyph in Club's interface is Onest. The full licence text ships with the fonts in
[`tools/fonts/OFL.txt`](tools/fonts/OFL.txt) and in the mod jar as `OFL_club`.

Two things worth knowing, because the filenames do not say so:

- The files in `tools/fonts/` are named `inter_*.ttf` for historical reasons — the UI was prototyped
  against Inter and the name outlived the switch. **They contain Onest.** The same name carries into the
  generated atlases (`assets/club/ui/font/msdf/inter_*.png`) and into `FontRegistry`.
- The jar does not carry the `.ttf` files. It carries a pre-rendered MSDF atlas generated from them by
  `./gradlew genMsdfAtlas` — a derivative work of the font, and so covered by the same licence.

Onest does not reserve its font name, so no renaming is required by the licence. The misleading filename
is ours to clean up, not a licence condition.

## Minecraft, Fabric

Club is a Fabric mod. It ships no Minecraft or Fabric code, and no Mojang assets: the HUD's chip icons
(`com.club.hud.PixelIcons`) recolour textures loaded from the player's own resource manager at runtime —
no pixel data of Mojang's is redistributed in this repository or in the jar.

Fabric Loader and Fabric API are dependencies, not bundled code. Both are Apache-2.0.

## Icons

Every icon in the interface is drawn for Club (50 SVG sources in `tools/icons/src/`) and compiled into an
SDF atlas by `buildSrc/src/main/java/club/tools/IconAtlasGen.java`. No third-party icon set is used.
