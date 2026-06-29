# Panel Widget — M2.2 Task 4 Report

## What was built

**Files created:**
- `src/test/java/com/club/ui/component/widget/PanelTest.java` — headless logic tests (3 tests)
- `src/main/java/com/club/ui/component/widget/Panel.java` — Panel widget implementation

## Implementation decisions

### Public API
Exactly per spec §3.3 (frozen):
- `Panel()` — empty panel
- `Panel(Component child)` — panel with initial child
- `Panel child(Component c)` — set/replace child (returns `this`)
- `Panel padding(Insets p)` — set padding (default `Insets.ZERO`, returns `this`)

### Render
Used `WidgetPaint.flatSurface(ctx, x, y, w, h, r, Tokens.surface().surface())` as mandated by the composition
rule — no hand-rolled `roundedRect` + `border` sequence in Panel itself. `WidgetPaint.flatSurface` internally
calls `roundedRect` + `border(subtle)`, keeping Panel clean and consistent with the shared snippet.

Child is clipped via `pushRoundedClip` / `popClip` using the same radius token (`Tokens.radius().md()`).

### Layout
- `measure()`: returns `child.measure(availW − padding, availH − padding)` + padding; empty panel returns
  padding dimensions only (no NPE per spec §2.1).
- `layout()`: calls `super.layout()` to assign own bounds, then places child in inner rect
  `(x + left, y + top, w − horizontal, h − vertical)`.

### Alloc-free hot path
No `new` in `render()` or `measure()`. All fields (`child`, `padding`) are stored at construction/setter time.

### Token usage
- `Tokens.radius().md()` — corner radius
- `Tokens.surface().surface()` — fill color
- `Tokens.border().subtle()` and `Tokens.border().thickness()` — via `WidgetPaint.flatSurface` (no literals)

### Composition rule
`WidgetPaint.flatSurface` was NOT modified to add Panel-specific logic — it remains "dumb" generic primitives.
The "Rule of three" did not trigger: Panel alone doesn't replicate a pattern already seen in 2+ other widgets
that would warrant extraction.

## Test commands and results

```
./gradlew.bat test --tests "com.club.ui.component.widget.PanelTest"
```
Result: **BUILD SUCCESSFUL** — 3/3 tests pass:
- `measureAddsPadding` — Panel with 8px all-sides padding and 40×20 child measures to 56×36
- `layoutPlacesChildInInnerRect` — child placed at (x+left, y+top) with (w−horiz, h−vert) bounds
- `emptyPanelMeasuresPaddingOnly` — empty Panel with 8px padding measures to 16×16

```
./gradlew.bat test --tests "com.club.ui.ArchitectureRuleTest"
```
Result: **BUILD SUCCESSFUL** — arch-guard green

## Concerns

None. All hard constraints satisfied: backend-only (via `ctx.renderer()`), tokens-only (no literals),
position-passive (child placed within assigned bounds), alloc-free hot path, public contract frozen per spec §3.3.
