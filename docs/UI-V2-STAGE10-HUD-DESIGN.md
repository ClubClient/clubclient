# UI V2 — Stage 10 Spec: HUD Visual Design ("light structure")

> Date: 2026-07-01 · Branch base: `feat/ui-v2-m2.2-core`
> Goal: give the four in-world HUD elements (**Target · Effects · Info · Armor**) a finished, cohesive visual
> design — a shared flat "panel" language — and bring **Armor onto the V2 stack** so all four share one design
> system + motion. Stays inside the flat language: no glass, no glow, no gradients on game data.

## 0. Constraints

- Flat design language (memory `club-design-reference`): neutral-dark tones, accent `#7CABFF`, **no glow/glass/
  gradient on data**; state shown by a thin line (Target HP) or a small dot (Armor durability), never by tinting
  digits.
- HUD renders **over the game world** — panels must stay readable AND not block the view (translucent).
- **Text cannot fade via `pushOpacity`** (backend limit, Stage 9 finding) — all HUD fades scale the text colour
  alpha with `Color.scaleAlpha`; panel shapes fade via alpha fill.
- Reuse existing tokens/motion primitives (`MotionState`/`Reveal`/`ValueTween`, `Color`, `Tokens`). No new client
  features, no changes to `ClubConfig`/save, no changes to the editor's setting set.

## 1. Locked decisions (confirmed 2026-07-01)

| # | Decision | Choice |
|---|----------|--------|
| D1 | Visual direction | **Light structure** — a subtle flat panel (translucent fill + hairline) behind each element, unifying all four. |
| D2 | Armor | **Migrate to V2** as a `HudElement`. Vanilla armor sprites keep rendering via `DrawContext` (hybrid); value/dot/panel on V2. Retire legacy `hud/ArmorHud`. |
| D3 | Panel density | **Subtle / translucent** — ~50% dark fill + a 1px hairline (world shows through). |
| D4 | Effects rows | **Text only** (`Name  Time`) — no per-effect duration line. |

## 2. Shared HUD panel language (the core of "light structure")

Every element sits on an identical flat panel so the four read as one system:

- **Shape:** rounded rect, radius `Tokens.radius().sm()` (6px), sized to the element's content box **+ inner padding
  ~`Tokens.spacing().sm()` (8px)** on every side.
- **Fill:** the darkest tone `surface().bg1()` (#0B111A) at **~50% alpha** (`Color.withAlpha(bg1, ~0x80)`) — tunable;
  world shows through.
- **Hairline:** 1px, `border().subtle()` (white ~6–10%). No shadow, no glow.
- **Scale:** the panel scales with the element's `cfgScale()` (padding + radius multiply by scale) so it always hugs
  the content.
- **Editor:** the element's drag/selection box = the panel box (bounds grow by the padding), so the hover/selection
  outline from `HudCanvas` frames the panel, not just the text.

Implementation: a small render-only helper (e.g. `HudPaint.panel(ctx, x, y, w, h, radius, alpha)`) drawn by
`HudElement.render()` **before** `paint(...)`, sized from the element's bounds. All four elements inherit it — a new
element gets the panel for free.

## 3. Per-element design

Content/hierarchy is unchanged from today except where noted; each now sits on the shared panel.

- **Target** — `Name` (white, SemiBold title) / `<hp> HP` (muted heading) / a 4px HP-fraction bar
  (steel-blue hue-ramp per the locked Hero etalon, HUD-LANGUAGE.md §8 — an earlier draft here said 2px).
  Bar spans the panel's inner width. *(HP-bar ease already shipped in Stage 9.3.)*
- **Effects** — a column (or row, per `potionHorizontal`) of `Name  Time` rows: name white, time muted. Panel height
  eases with the effect count; rows fade in on gain *(Stage 9.3)*. No duration line (D4).
- **Info** — `FPS <n>` and `XYZ <x / y / z>` on the panel. Digits stay as currently rendered (FPS accent / XYZ white);
  colour treatment is out of scope for this stage — only the panel is added.
- **Armor (V2)** — a column of `[sprite] value ●` (vertical) or a row of cells (horizontal, per `armorVertical`):
  vanilla armor **sprite** (drawn via `DrawContext`), **value** white SemiBold (`407/407` or `92%` per `armorPercent`),
  and a **durability dot** (green ≥70% / amber ≥40% / red <40%) whose colour **eases** across thresholds. Empty slots
  skipped. All on the shared panel.

## 4. Motion

- **Element appear / disappear:** when an element gains content (target acquired, first effect, armor equipped) it
  **fades in** (panel alpha + text `scaleAlpha` together via a per-element `Reveal`); when it loses content it fades
  out. `HudCanvas` keeps rendering a fading-out element until its `Reveal` is `gone()`, instead of the current hard
  `continue` cut. In-world only (editor shows all elements solid).
- **Values:** HP bar ease + threshold colour *(shipped 9.3)*; Armor durability **dot colour eases** across
  green/amber/red; optional lightly-smoothed armor value is out of scope (value text stays exact — truth).
- **Effects rows:** enter-fade on gain *(shipped 9.3)*; exit still instant (deferred — the expiring-first list
  reorders).
- All from existing duration/easing tokens; no overshoot/scale/glow.

## 5. Armor → V2 migration (architecture)

- New `com.club.ui.hud.ArmorElement extends HudElement` alongside `Target/Effects/Info`. Config binding to
  `ClubConfig.Hud` armor fields (`armor`, `armorPercent`, `armorVertical`, `armorX/Y`, `armorScale`); `contentSize`
  ports `ArmorHud.sizeOf`; `hasContent` = has ≥1 equipped piece (editor shows a diamond sample).
- **Sprites:** the V2 `UiRenderer` can't draw item icons, but the in-world HUD render path (`HudManager`) and the
  editor (`HudEditorScreen`) both have a Minecraft `DrawContext`. Expose it to `ArmorElement.paint` (e.g. a
  per-frame `HudSprites.set(drawContext)` seam or a `DrawContext` field the canvas threads through) so armor draws
  `drawContext.drawItem(stack, sx, sy)` for the sprite and the V2 `UiRenderer` for value/dot/panel. This DrawContext
  seam is the only non-V2 dependency and is confined to `ArmorElement`.
- Wire `ArmorElement` into the in-world `HudManager` canvas and the `HudEditorScreen` canvas; the editor's existing
  Armor settings rows (Orientation / Value) bind to it. **Remove legacy `hud/ArmorHud`** from `HudManager` once the
  V2 element is in.
- Retire `armor` from the legacy dispatch; keep `ClubConfig` fields (save-compatible).

## 6. Also in this stage

- **`Dropdown` widget motion (Stage 9 part-1 finding):** the standalone `Dropdown` (HUD editor Orientation control)
  has no hover/press animation. Add a `MotionState` hover + eased press overlay (it is NOT one of the frozen-four
  widgets) so it matches Toggle/Slider/Button.

## 7. Phased plan (each: compile → test → report → STOP for confirmation, unless owner authorises autonomous run)

| Stage | Scope | Files | Risk |
|-------|-------|-------|------|
| **10.1** | Shared panel system — `HudPaint.panel` + `HudElement` draws it (with padded bounds); Target/Effects/Info get panels. | `hud/HudPaint.java` (new), `hud/HudElement.java`, minor per-element inset | low–med |
| **10.2** | Armor → V2 — `ArmorElement` + DrawContext sprite seam; wire into `HudManager` + `HudEditorScreen`; retire legacy `ArmorHud`. | `hud/ArmorElement.java` (new), `hud/HudElement.java`, `hud/HudCanvas.java`, `hud/HudEditorScreen.java`, `hud/HudManager.java` | med |
| **10.3** | Element appear/disappear fade (panel + text) via per-element `Reveal` in `HudCanvas` (in-world). | `hud/HudCanvas.java`, `hud/HudElement.java` | med |
| **10.4** | Armor durability dot colour ease; `Dropdown` widget hover/press motion. | `hud/ArmorElement.java`, `component/widget/Dropdown.java` | low |
| **10.5** | Full build + test green; report. | — | — |

**Ordering:** panel first (defines the look) → Armor V2 (biggest structural piece, needs the panel) → appear/exit
motion → small polish.

## 7b. Delivered (2026-07-01)

All of 10.1–10.4 implemented, `./gradlew build` green, committed `fbb21bb`…`b070861` on
`feat/ui-v2-m2.2-core` (owner authorised autonomous run). Notes:
- **10.1** `HudPaint.panel` + `HudElement` draws it (padded bounds, `alpha` field for the fade).
- **10.2** `ArmorElement` on V2; `HudSprites` DrawContext seam (set by `HudManager` + editor); wired into both
  canvases + editor settings (Layout/Value) + type branches; legacy `hud/ArmorHud` deleted.
- **10.3** `HudCanvas` fades elements in/out with content (per-element `alpha` Transition); all V2 paints scale
  colours by `alpha`.
- **10.4** armor dot smooth threshold crossing; `Dropdown` eased hover/press (`MotionState` + new
  `WidgetPaint.pressOverlay(...,t)`).
- **Runtime to verify in-game:** armor sprites draw via `DrawContext` interleaved with the V2 pass — confirm
  z-order/state; panel alpha (~50%) legibility over bright backgrounds; content-position shift from the new
  padding.

## 8. Acceptance criteria

- All four HUD elements share one panel (same radius/padding/fill/hairline), scale correctly, and read clearly over
  varied world backgrounds at ~50% panel alpha.
- Armor renders on the V2 stack (sprites via DrawContext), legacy `ArmorHud` removed from the dispatch; editor Armor
  settings still work.
- Elements fade in/out with content in-world; no layout/spacing/token/config/behaviour changes beyond the panel +
  Armor migration; existing tests green; `./gradlew build` compiles each stage.
- No glow/glass/gradient-on-data; flat language preserved.
