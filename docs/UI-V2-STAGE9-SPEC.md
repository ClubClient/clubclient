# UI V2 — Stage 9 Spec: Motion, Icon & HUD Polish

> Date: 2026-07-01 · Branch base: `feat/ui-v2-m2.2-core`
> Goal: bring the whole V2 UI to a finished, commercial-product feel (Raycast / Linear / Notion restraint)
> by making state changes **live** — a single Motion System + procedural Icon extensions + HUD polish.
> **We do not redesign. We make the existing design move.**

## 0. Hard constraints (frozen — do NOT modify)

Render backend · `Ui` / `UiContext` / `UiRenderer` / `UiText` / `UiShaders` · Theme / Palette / Surface /
Border / Typography / **Motion tokens** / Interaction · `WidgetPaint` call-sequences (additive helpers only) ·
`ClubConfig` + save system · all layout / spacing / sizes / paddings / colours · widget architecture / API /
behaviour. No new client features, no ArrayList/Keystrokes/CPS/Notifications/Armor-V2, no "drive-by" refactor.

**Allowed:** add animation state to widgets/screens/HUD; extend the `Icon` enum; add motion helpers; the single
carve-out the freeze permits — additive `WidgetPaint` overloads.

## 1. Locked decisions (confirmed 2026-07-01)

| # | Decision | Choice |
|---|----------|--------|
| D1 | Spring/overshoot easing | **No.** Stay within existing curves (`STANDARD` / `DECELERATE` / `ACCELERATE` / `LINEAR`). No element ever moves past its final position. |
| D2 | Refactor the 4 already-animated widgets (Button/Toggle/Slider/Checkbox) onto MotionState | **No.** They keep their hand-tuned timings untouched. `MotionState` is for the hand-drawn screens + future widgets only. |
| D3 | Icons | **Yes, procedural only.** Add rail **category** icons + a **search** glyph (existing `Icon` enum values). Cards stay text-only. No SVG/MSDF pack (backend frozen). |
| D4 | Order after infra | `ScrollArea` (base component, used everywhere) is perfected **first** at 9.2, then the headline **HUD** polish at 9.3. *(Refined 2026-07-01: finish the shared base component before its consumers rely on it; icons stay last — decided once the UI is "alive".)* |

## 2. Motion infrastructure — what exists & the gaps

Exists: `motion/Transition` (one animated float off `ctx.time()`), `motion/Curves` (4 easings, **no spring**),
`Motion` tokens (`fast .12 / normal .20 / slow .32`), `Interaction` (`hoverWash / pressOverlay / disabledAlpha /
focusRing`), `WidgetPaint.hoverWash(...,t)` (alpha-scaled).

| Gap | Description |
|-----|-------------|
| G1 | No shared per-component animator — every widget re-declares its own `Transition`; new widgets start instant. |
| G2 | `Transition` is a single scalar — no home for list enter/exit, popover/screen open (alpha+offset + reverse-on-close), or multi-property shared factors. |
| G3 | `WidgetPaint.pressOverlay` / `focusRing` are **binary** — no fade even where hover animates. |
| G4 | No numeric value-tween for HUD numbers. |
| G5 | `Interaction.disabledAlpha` is dead; `Label` ignores `enabled` — disabled state has no visual at all. |
| G6 | `ClubMenuScreen` & `HudEditorScreen` hand-draw state (`if(hovered)…else…`), bypassing the animated widget layer. **The core Stage 9 problem is architectural, not per-widget.** |

## 3. Already animated (do not touch)

Button hover-fill (`fast/standard`) · Toggle knob-slide (`normal/standard`) + grow (`fast/decelerate`) ·
Slider knob-grow (`fast/decelerate`) · Checkbox check-reveal (`fast/decelerate`) + hover (`fast/standard`) ·
menu rail sliding accent indicator (`normal/standard`). *(Search caret = hard 2 Hz flicker → treated as a
defect to smooth, not existing motion.)*

## 4. Instant state changes (the work), by area

- **HUD (Tier D — first):** `TargetElement` HP bar fraction + threshold colour snap + HP/name text hard-cut;
  `EffectsElement` rows pop in/out + box-height jump + `hasContent` on/off; `InfoElement` FPS jitter;
  `HudCanvas` hover outline / selection border / snap guides pop.
- **ScrollArea:** thumb has **no** hover/drag visual; wheel scroll hard-jumps; `popScroll` offset resets on rebuild.
- **Menu tiles/rail:** ModuleTile fill/edge/name on enable + on hover; rail active-pill teleport; rail-row text colour.
- **Menu inputs:** SearchField border (focus/hover); placeholder↔typed-text swap; OptionRow selected/hover.
- **Overlays:** settings popover open/close/resize (both screens); dropdown pick-list expand; tab-segment swap.
- **Entrances:** both screens paint at full opacity from frame 1; category-switch grid hard-cut.
- **Roots (opt-in):** binary focus ring; container child enter/exit; focus jump between widgets; disabled dimming (G5).

## 5. Motion System architecture (all additive)

### 5.1 `com.club.ui.motion.MotionState` (fixes G1, feeds G3)
Per-component bundle of interaction Transitions, each built from existing tokens:
```
hover   = Transition(0, motion.fast(),   easings.standard())
press   = Transition(0, motion.fast(),   easings.decelerate())
focus   = Transition(0, motion.fast(),   easings.standard())
enabled = Transition(1, motion.normal(), easings.standard())
```
- `update(Component c, float now)` targets each channel from `c.isHovered()/isPressed()/isFocused()/enabled`.
- Getters `hover(now)/press(now)/focus(now)/disabled(now)` → progress in [0,1].
- Called once at the **top of a widget's `render()`** where `now = ctx.time()` exists — so **no input signature
  changes** (avoids the "no ctx in mouseMoved" problem).
- **Opt-in** (D2): consumed by the two hand-drawn screens' hand-rolled sub-components and any new widget. The 4
  shipped widgets are NOT migrated.

### 5.2 `WidgetPaint` `t`-aware overloads (fixes G3 — the permitted carve-out)
Add `focusRing(ctx, c, radius, float t)` (alpha-scale ring by `t`) and `pressOverlay(ctx, …, float t)`. Keep the
existing binary signatures unchanged.

### 5.3 `com.club.ui.motion.Reveal` (fixes G2 — enter/exit)
Wraps a `Transition(0→1)` + a `closing` flag. Plays **in** on insert; `requestClose()` plays **out**, and the owner
drops it only when **fully closed**, defined as `value(now) <= EPS` (**not** `Transition.animating()`, which returns
false on a same-frame open→close). Multiplies child render alpha and/or reveals measured height via clip. Owned by
concrete containers (a `Map<Component,Reveal>`), never forced into base `Container`.

### 5.4 `com.club.ui.motion.ValueTween` (fixes G4 — HUD numbers)
A `Transition` specialised for data: `set(raw, now)` / `get(now)`. **HP bar fill only**, short (`fast`) and **capped
so it never lags real value > ~100 ms** (combat honesty). HP/name **text stays instant**. XYZ stays instant. FPS
optional light smoothing.

### 5.5 Correctness rules
- Live Slider/Window **drag stays 1:1** with the cursor — only programmatic moves ease.
- `pushOpacity` nests multiplicatively — a popover open-fade wrapping a disabled child must be verified so it doesn't
  double-dim (sequence dimming after popovers to test this).
- Preserve `ScrollArea.offset` across `rebuildGrid()` / `rebuildPopover()` before animating those transitions.

## 6. Icon plan (D3)
Backend renders diagonals as bounding boxes → stay procedural. Extend the enum **only alongside a consumer**.
- Rail rows: draw each `Category.icon` (data already populated in `MenuContent`) at the row's left.
- SearchField: draw `Icon.SEARCH` (diagonal-free) in the field's left padding.
- **Not** on cards (Stage 6/7 removed them by design), not in popovers/HUD.
- Note: `MOVEMENT` + `EXPLOIT` enum values are currently unused; no new values are required for this scope.

## 7. Phased plan (each stage: compile → test → brief report → **STOP for confirmation**)

| Stage | Scope | Files | Risk |
|-------|-------|-------|------|
| **9.1** | Motion infra only — `MotionState`, `Reveal`, `ValueTween`, `WidgetPaint` `t`-overloads. Nothing wired → no visible change. | `motion/MotionState.java`, `motion/Reveal.java`, `motion/ValueTween.java` (new), `component/widget/WidgetPaint.java` (+overloads), optional `Component.java` accessors | low |
| **9.2** | ScrollArea thumb hover/drag colour + optional smoothed wheel; preserve offset on rebuild. Base component used by the menu grid, every popover, and future screens — finished before its consumers. | `component/widget/ScrollArea.java` | low–med |
| **9.3** | HUD in-world: HP bar tween + threshold colour lerp (text instant), effect-row enter/exit + smoothed box height, canvas hover/selection/guide fades. | `hud/TargetElement.java`, `hud/EffectsElement.java`, `hud/HudCanvas.java`, opt. `hud/InfoElement.java` | med |
| **9.4** | Menu tiles + rail: per-tile `enabled`(fast) + `hover`(fast) lerps for fill/edge/name; rail pill crossfade reusing `indicator`; rail-row text lerp. | `menu/ClubMenuScreen.java` | med |
| **9.5** | Menu inputs: SearchField border lerp + placeholder/text crossfade + caret alpha-fade; OptionRow selected/hover Transitions. | `menu/ClubMenuScreen.java` | low–med |
| **9.6** | Popovers & reveals: open/close (alpha + few-px Y, reverse-before-clear) + animate `popH/popY`; dropdown pick-list `Reveal`; both screens. | `menu/ClubMenuScreen.java`, `hud/HudEditorScreen.java` | med |
| **9.7** | Entrances: scrim + window fade-in (no scale), grid retile crossfade/stagger on category/query change. | `menu/ClubMenuScreen.java`, `hud/HudEditorScreen.java` | med |
| **9.8** | Base disabled-dimming via `enabled` channel → `pushOpacity`/`scaleAlpha` toward `disabledAlpha`; `Label.colorValue()` honours `enabled`. | `component/widget/Label.java`, consumers | low |
| **9.9** | Icons (D3): rail category icons + search glyph. | `menu/ClubMenuScreen.java` (+`Icon.java` only if a new diagonal-free constant is actually consumed) | low |

**Ordering rationale:** 9.1 lands the invisible foundation → 9.2 perfects `ScrollArea` first — a base component
the menu grid, every popover, and future screens depend on, so downstream stages build on finished infra →
9.3 delivers the headline HUD mandate → 9.4–9.7 tackle the two hand-drawn screens in rising lifecycle
complexity → 9.8 base dimming → 9.9 icons last (decided after everything is "alive", when it's clear where an
icon reads richer than text).

## 8. Global acceptance criteria
- Every listed instant swap becomes an eased transition using **existing** duration/easing tokens.
- No change to layout, spacing, sizes, colours, tokens, config, or widget behaviour/API.
- Motion reads crisp & restrained (no glow/scale/overshoot/"cheat-client" flair).
- All existing tests stay green; `./gradlew build` compiles each stage; new helpers unit-tested where headless-safe.
- A new widget/HUD element that adopts `MotionState` is animated with zero extra per-widget wiring.
