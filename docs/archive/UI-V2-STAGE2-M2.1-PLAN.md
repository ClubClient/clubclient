# Stage 2 / M2.1 Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the non-visual Foundation of the Stage 2 design system — token system, layout engine, motion
framework, and component/container/focus base — fully tested headlessly, with zero real widgets.

**Architecture:** Pure-Java (MC-free) layers under `com.club.ui`: `theme/` (immutable token records + ClubDark
default + `Tokens` facade), `layout/` (value types + hybrid Column/Row/Stack/Spacer engine), `motion/`
(Easing curves + Transition), `component/` (Component, Container, FocusManager, UiContextImpl). Everything builds
on the frozen Stage 1 contracts (`UiRenderer/UiText/Ui/UiContext`, `Color`, `text.Weight`). Spec:
`docs/UI-V2-STAGE2-SPEC.md` (§3 tokens, §4 layout, §5 component, §6 motion).

**Tech Stack:** Java 21, Fabric 1.21.1 (Foundation itself is MC-free), JUnit 5 (Jupiter), Gradle.

## Global Constraints

- **backend-only:** no `DrawContext`/`RenderSystem`/`GlUniform`/`ClubFont`/`RenderHelper` anywhere here; render
  only via `UiContext.renderer()` / `UiContext.text()`. `ArchitectureRuleTest` stays green.
- **Tokens = single source of truth:** NO literals for color/radius/size/spacing/duration in `layout/`, `motion/`
  (runtime), `component/`. The ONLY sanctioned home for value literals is `theme/themes/*`.
- **Position-passive components:** `x,y,w,h` are assigned by `layout(...)`, read-only for `render()`. Components
  never compute their own/siblings' coordinates. Only containers measure/lay out.
- **No premature optimization:** allow (don't implement) virtualization/caching/batching. Keep API clean.
- **Alloc rule:** no allocation inside `render()`. Layout buffers are reused fields, not per-call garbage.
- **Foundation self-contained:** no real widgets in M2.1.
- **Java 21**, package root `com.club.ui`. Colors packed `0xAARRGGBB` via `com.club.ui.Color`.
- **Branch:** work on `feat/ui-v2-m2.1-foundation` off `main`. Each commit message ends with:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- **Test run:** `.\gradlew.bat test --tests "<FQN>"` (single class) / `.\gradlew.bat test` (all) /
  `.\gradlew.bat compileJava` (compile check).

---

## File Structure

```
src/main/java/com/club/ui/
  layout/   Size · Sizing · Insets · CrossAlign · MainAlign · Anchor   (Task 1)
            Spacer (Task 6) · Linear · Column · Row (Task 7) · Stack (Task 8)
  motion/   Easing · Curves · Transition                               (Task 2)
  theme/    Palette Surface Accent Border Shadow Glow Radius Spacing
            Typography Elevation Motion Theme · Tokens                 (Task 3)
            themes/ClubDark                                            (Task 3)
  component/ Component (Task 4) · Container (Task 5)
             FocusManager (Task 9) · UiContextImpl (Task 10)
src/test/java/com/club/ui/
  layout/ … motion/ … theme/ … component/ …                           (per task)
```

Dependency order is encoded in task numbering (each task only consumes earlier tasks + Stage 1).

---

### Task 1: Layout value types

**Files:**
- Create: `src/main/java/com/club/ui/layout/Size.java`
- Create: `src/main/java/com/club/ui/layout/Sizing.java`
- Create: `src/main/java/com/club/ui/layout/Insets.java`
- Create: `src/main/java/com/club/ui/layout/CrossAlign.java`
- Create: `src/main/java/com/club/ui/layout/MainAlign.java`
- Create: `src/main/java/com/club/ui/layout/Anchor.java`
- Test: `src/test/java/com/club/ui/layout/LayoutValueTypesTest.java`

**Interfaces:**
- Produces: `record Size(float w, float h)`; `sealed interface Sizing` with `Sizing.Fixed/Fill/Weight(float value)`
  + statics `Sizing.fixed()/fill()/weight(float)`; `record Insets(float top,right,bottom,left)` with
  `Insets.ZERO`, `all(float)`, `symmetric(float h,float v)`, `horizontal()`, `vertical()`;
  `enum CrossAlign { START, CENTER, END, STRETCH }`; `enum MainAlign { START, CENTER, END }`;
  `enum Anchor { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT }`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.layout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutValueTypesTest {
    @Test void insetsAllAndSymmetric() {
        Insets a = Insets.all(4);
        assertEquals(4, a.top()); assertEquals(4, a.left()); assertEquals(8, a.horizontal()); assertEquals(8, a.vertical());
        Insets s = Insets.symmetric(10, 6); // h=10, v=6
        assertEquals(6, s.top()); assertEquals(10, s.right()); assertEquals(6, s.bottom()); assertEquals(10, s.left());
        assertEquals(0, Insets.ZERO.horizontal());
    }
    @Test void sizingVariants() {
        assertTrue(Sizing.fixed() instanceof Sizing.Fixed);
        assertTrue(Sizing.fill()  instanceof Sizing.Fill);
        Sizing w = Sizing.weight(2.5f);
        assertTrue(w instanceof Sizing.Weight);
        assertEquals(2.5f, ((Sizing.Weight) w).value());
    }
    @Test void sizeFields() { Size sz = new Size(3, 7); assertEquals(3, sz.w()); assertEquals(7, sz.h()); }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.LayoutValueTypesTest"`
Expected: FAIL — compilation error, `Size`/`Sizing`/`Insets` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// Size.java
package com.club.ui.layout;
public record Size(float w, float h) {}
```
```java
// Sizing.java
package com.club.ui.layout;
public sealed interface Sizing permits Sizing.Fixed, Sizing.Fill, Sizing.Weight {
    record Fixed()  implements Sizing {}
    record Fill()   implements Sizing {}
    record Weight(float value) implements Sizing {}
    Fixed FIXED = new Fixed();
    Fill  FILL  = new Fill();
    static Sizing fixed()        { return FIXED; }
    static Sizing fill()         { return FILL; }
    static Sizing weight(float w) { return new Weight(w); }
}
```
```java
// Insets.java
package com.club.ui.layout;
public record Insets(float top, float right, float bottom, float left) {
    public static final Insets ZERO = new Insets(0, 0, 0, 0);
    public static Insets all(float v)               { return new Insets(v, v, v, v); }
    public static Insets symmetric(float h, float v) { return new Insets(v, h, v, h); }
    public float horizontal() { return left + right; }
    public float vertical()   { return top + bottom; }
}
```
```java
// CrossAlign.java
package com.club.ui.layout;
public enum CrossAlign { START, CENTER, END, STRETCH }
```
```java
// MainAlign.java
package com.club.ui.layout;
public enum MainAlign { START, CENTER, END }
```
```java
// Anchor.java
package com.club.ui.layout;
public enum Anchor { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.LayoutValueTypesTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/layout/ src/test/java/com/club/ui/layout/LayoutValueTypesTest.java
git commit -m "feat(ui): layout value types (Size/Sizing/Insets/aligns) [M2.1]"
```

---

### Task 2: Motion — Easing, Curves, Transition

**Files:**
- Create: `src/main/java/com/club/ui/motion/Easing.java`
- Create: `src/main/java/com/club/ui/motion/Curves.java`
- Create: `src/main/java/com/club/ui/motion/Transition.java`
- Test: `src/test/java/com/club/ui/motion/MotionTest.java`

**Interfaces:**
- Produces: `@FunctionalInterface interface Easing { float apply(float t); }`;
  `Curves.LINEAR/STANDARD/DECELERATE/ACCELERATE` (Easing constants);
  `Transition(float initial, float duration, Easing easing)` with `void target(float v, float now)`,
  `float value(float now)`, `boolean animating(float now)`, `float target()`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.motion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionTest {
    @Test void curvesHitEndpoints() {
        for (Easing e : new Easing[]{Curves.LINEAR, Curves.STANDARD, Curves.DECELERATE, Curves.ACCELERATE}) {
            assertEquals(0f, e.apply(0f), 1e-5);
            assertEquals(1f, e.apply(1f), 1e-5);
            assertTrue(e.apply(0.49f) <= e.apply(0.51f), "easing must be monotonic non-decreasing");
        }
        assertEquals(0f, Curves.STANDARD.apply(-1f), 1e-5, "input clamped to 0");
        assertEquals(1f, Curves.STANDARD.apply(2f), 1e-5, "input clamped to 1");
    }
    @Test void transitionInterpolatesAndSettles() {
        Transition tr = new Transition(0f, 0.2f, Curves.LINEAR);
        tr.target(1f, 0f);
        assertEquals(0f, tr.value(0f), 1e-5);
        assertEquals(0.5f, tr.value(0.1f), 1e-5);
        assertEquals(1f, tr.value(0.2f), 1e-5);
        assertEquals(1f, tr.value(5f), 1e-5);
        assertTrue(tr.animating(0.1f));
        assertFalse(tr.animating(0.25f));
    }
    @Test void retargetMidFlightStartsFromCurrent() {
        Transition tr = new Transition(0f, 0.2f, Curves.LINEAR);
        tr.target(1f, 0f);
        tr.target(0f, 0.1f);                 // reverse from current 0.5
        assertEquals(0.5f, tr.value(0.1f), 1e-5);
        assertEquals(0f, tr.value(0.3f), 1e-5);
    }
    @Test void zeroDurationIsInstant() {
        Transition tr = new Transition(0f, 0f, Curves.LINEAR);
        tr.target(1f, 0f);
        assertEquals(1f, tr.value(0f), 1e-5);
        assertFalse(tr.animating(0f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.motion.MotionTest"`
Expected: FAIL — `Easing`/`Curves`/`Transition` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// Easing.java
package com.club.ui.motion;
@FunctionalInterface
public interface Easing { float apply(float t); }
```
```java
// Curves.java
package com.club.ui.motion;
public final class Curves {
    private Curves() {}
    private static float c(float t) { return t < 0f ? 0f : (t > 1f ? 1f : t); }
    public static final Easing LINEAR     = t -> c(t);
    public static final Easing STANDARD   = t -> { float x = c(t); return x * x * (3f - 2f * x); }; // smoothstep
    public static final Easing DECELERATE = t -> { float x = c(t); return 1f - (1f - x) * (1f - x); }; // ease-out
    public static final Easing ACCELERATE = t -> { float x = c(t); return x * x; };                    // ease-in
}
```
```java
// Transition.java
package com.club.ui.motion;
/** One animated scalar. Driven by an external clock (UiContext.time(), seconds). Held as a component field. */
public final class Transition {
    private final float duration;
    private final Easing easing;
    private float from, to, start;
    public Transition(float initial, float duration, Easing easing) {
        this.from = this.to = initial;
        this.duration = Math.max(0f, duration);
        this.easing = easing;
        this.start = 0f;
    }
    public void target(float v, float now) {
        if (v == to) return;
        this.from = value(now);
        this.to = v;
        this.start = now;
    }
    public float value(float now) {
        if (duration <= 0f) return to;
        float t = (now - start) / duration;
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        return from + (to - from) * easing.apply(t);
    }
    public boolean animating(float now) { return duration > 0f && (now - start) < duration && from != to; }
    public float target() { return to; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.motion.MotionTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/motion/ src/test/java/com/club/ui/motion/MotionTest.java
git commit -m "feat(ui): motion runtime — Easing curves + Transition [M2.1]"
```

---

### Task 3: Token system — records + ClubDark + Tokens facade

**Files:**
- Create records in `src/main/java/com/club/ui/theme/`: `Palette.java`, `Surface.java`, `Accent.java`,
  `Border.java`, `Shadow.java`, `Glow.java`, `Radius.java`, `Spacing.java`, `Typography.java`, `Elevation.java`,
  `Motion.java`, `Theme.java`
- Create: `src/main/java/com/club/ui/theme/Tokens.java`
- Create: `src/main/java/com/club/ui/theme/themes/ClubDark.java`
- Test: `src/test/java/com/club/ui/theme/TokensTest.java`

**Interfaces:**
- Consumes: `com.club.ui.text.Weight` (REGULAR/MEDIUM/SEMIBOLD), `com.club.ui.motion.Easing` + `Curves`.
- Produces: records per spec §3; `Tokens` static facade: `palette() radius() spacing() type() surface() accent()
  border() shadow() glow() elevation() motion()` + `theme()` + `setTheme(Theme)`; `ClubDark.create() -> Theme`.
  Field names are frozen by spec §3 (e.g. `Surface.bg1`, `Accent.accent`, `Radius.md`, `Border.defaultColor`,
  `Typography.title`, `Shadow.Preset(dx,dy,blur,color)`, `Glow.Preset(size,color)`,
  `Elevation.Level(surface,border,shadow,glow)`, `Motion.Durations(instant,fast,normal,slow)`).

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.theme;
import com.club.ui.text.Weight;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokensTest {
    @AfterEach void reset() { Tokens.setTheme(com.club.ui.theme.themes.ClubDark.create()); }

    @Test void defaultThemeHasAllGroups() {
        assertNotNull(Tokens.palette()); assertNotNull(Tokens.radius()); assertNotNull(Tokens.spacing());
        assertNotNull(Tokens.type()); assertNotNull(Tokens.surface()); assertNotNull(Tokens.accent());
        assertNotNull(Tokens.border()); assertNotNull(Tokens.shadow()); assertNotNull(Tokens.glow());
        assertNotNull(Tokens.elevation()); assertNotNull(Tokens.motion());
    }
    @Test void clubDarkValues() {
        assertEquals(0xFF0B111A, Tokens.surface().bg1());
        assertEquals(0xFF7CABFF, Tokens.accent().accent());
        assertEquals(0xFF78D7FF, Tokens.accent().gradientB());
        assertEquals(10f, Tokens.radius().md());
        assertEquals(6f,  Tokens.radius().sm());
        assertEquals(12f, Tokens.spacing().md());
        assertEquals(0.20f, Tokens.motion().durations().normal(), 1e-6);
    }
    @Test void surfaceReferencesPalette() {
        assertEquals(Tokens.palette().ink2(), Tokens.surface().bg1());   // bg1 == ink2 (#0B111A)
        assertEquals(Tokens.palette().accent(), Tokens.accent().accent());
    }
    @Test void typographyTitleRole() {
        Typography.Role t = Tokens.type().title();
        assertEquals(Weight.SEMIBOLD, t.weight());
        assertEquals(16f, t.size());
        assertEquals(22f, t.lineHeight());
    }
    @Test void elevationLevel1HasShadow() {
        Elevation.Level l1 = Tokens.elevation().level1();
        assertNotNull(l1.shadow());
        assertEquals(Tokens.surface().surface(), l1.surface());
    }
    @Test void setThemeSwaps() {
        Theme base = ClubDarkRef();
        Theme alt = new Theme(base.palette(), new Radius(1,2,3,4,5), base.spacing(), base.type(), base.surface(),
            base.accent(), base.border(), base.shadow(), base.glow(), base.elevation(), base.motion());
        Tokens.setTheme(alt);
        assertEquals(3f, Tokens.radius().md());
    }
    private static Theme ClubDarkRef() { return com.club.ui.theme.themes.ClubDark.create(); }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.theme.TokensTest"`
Expected: FAIL — token types not found.

- [ ] **Step 3: Write minimal implementation**

Records (one file each):

```java
// Palette.java
package com.club.ui.theme;
public record Palette(
    int ink0, int ink1, int ink2, int ink3, int ink4, int ink5, int ink6, int ink7, int ink8,
    int accent, int accent2,
    int textHi, int textMuted, int textDesc, int textFaint,
    int stateGood, int stateWarn, int stateLow,
    int white) {}
```
```java
// Surface.java
package com.club.ui.theme;
public record Surface(int bg0, int bg1, int bg2, int surface, int surfaceHi) {}
```
```java
// Accent.java
package com.club.ui.theme;
public record Accent(int accent, int accentHi, int gradientA, int gradientB) {}
```
```java
// Border.java
package com.club.ui.theme;
public record Border(int subtle, int defaultColor, int strong, float thickness) {}
```
```java
// Shadow.java
package com.club.ui.theme;
public record Shadow(Preset sm, Preset md, Preset lg) {
    public record Preset(float dx, float dy, float blur, int color) {}
}
```
```java
// Glow.java
package com.club.ui.theme;
public record Glow(Preset subtle, Preset active) {
    public record Preset(float size, int color) {}
}
```
```java
// Radius.java
package com.club.ui.theme;
public record Radius(float xs, float sm, float md, float lg, float xl) {}
```
```java
// Spacing.java
package com.club.ui.theme;
public record Spacing(float xs, float sm, float md, float lg, float xl, float xxl) {}
```
```java
// Typography.java
package com.club.ui.theme;
import com.club.ui.text.Weight;
public record Typography(Role display, Role title, Role heading, Role body, Role label, Role caption) {
    public record Role(Weight weight, float size, float lineHeight) {}
}
```
```java
// Elevation.java
package com.club.ui.theme;
public record Elevation(Level level0, Level level1, Level level2, Level level3) {
    /** glow may be null (no glow at this level). */
    public record Level(int surface, int border, Shadow.Preset shadow, Glow.Preset glow) {}
}
```
```java
// Motion.java
package com.club.ui.theme;
import com.club.ui.motion.Easing;
public record Motion(Durations durations, Easings easings) {
    public record Durations(float instant, float fast, float normal, float slow) {}
    public record Easings(Easing standard, Easing decelerate, Easing accelerate, Easing linear) {}
}
```
```java
// Theme.java
package com.club.ui.theme;
public record Theme(Palette palette, Radius radius, Spacing spacing, Typography type, Surface surface,
                    Accent accent, Border border, Shadow shadow, Glow glow, Elevation elevation, Motion motion) {}
```
```java
// Tokens.java
package com.club.ui.theme;
import com.club.ui.theme.themes.ClubDark;
/** Active-theme facade. The single access point for all design tokens. */
public final class Tokens {
    private Tokens() {}
    private static Theme active = ClubDark.create();
    public static void setTheme(Theme t) { active = t; }
    public static Theme theme()        { return active; }
    public static Palette    palette()  { return active.palette(); }
    public static Radius     radius()   { return active.radius(); }
    public static Spacing    spacing()  { return active.spacing(); }
    public static Typography type()     { return active.type(); }
    public static Surface    surface()  { return active.surface(); }
    public static Accent     accent()   { return active.accent(); }
    public static Border     border()   { return active.border(); }
    public static Shadow     shadow()   { return active.shadow(); }
    public static Glow       glow()     { return active.glow(); }
    public static Elevation  elevation(){ return active.elevation(); }
    public static Motion     motion()   { return active.motion(); }
}
```

ClubDark default (values from spec §3.8 — this file is the sanctioned home for literals):

```java
// themes/ClubDark.java
package com.club.ui.theme.themes;
import com.club.ui.Color;
import com.club.ui.motion.Curves;
import com.club.ui.text.Weight;
import com.club.ui.theme.*;

public final class ClubDark {
    private ClubDark() {}
    public static Theme create() {
        Palette p = new Palette(
            0xFF06090F, 0xFF090E16, 0xFF0B111A, 0xFF0C1320, 0xFF0F1624, 0xFF131B2A, 0xFF18212F, 0xFF1D2536, 0xFF222A38,
            0xFF7CABFF, 0xFF78D7FF,
            0xFFF4F6FA, 0xFFA6ADBB, 0xFF767E8E, 0xFF5A6273,
            0xFF2ECC71, 0xFFE3C66A, 0xFFE06B6B,
            0xFFFFFFFF);

        Surface surface = new Surface(p.ink0(), p.ink2(), p.ink4(), p.ink5(), p.ink6());
        Accent accent  = new Accent(p.accent(), 0xFF93BBFF, p.accent(), p.accent2());
        Border border  = new Border(Color.withAlpha(p.white(), 0x0F), 0xFF1D2536, 0xFF2A3550, 1f); // subtle = white@~6%
        Radius radius  = new Radius(4f, 6f, 10f, 14f, 20f);
        Spacing spacing = new Spacing(4f, 8f, 12f, 16f, 24f, 32f);

        Typography type = new Typography(
            new Typography.Role(Weight.SEMIBOLD, 20f, 26f),  // display
            new Typography.Role(Weight.SEMIBOLD, 16f, 22f),  // title
            new Typography.Role(Weight.MEDIUM,   15f, 20f),  // heading
            new Typography.Role(Weight.MEDIUM,   13f, 18f),  // body
            new Typography.Role(Weight.MEDIUM,   12f, 16f),  // label
            new Typography.Role(Weight.REGULAR,  12f, 16f)); // caption

        Shadow shadow = new Shadow(
            new Shadow.Preset(0f, 1f, 4f,  Color.withAlpha(0xFF000000, 0x40)),
            new Shadow.Preset(0f, 4f, 12f, Color.withAlpha(0xFF000000, 0x4D)),
            new Shadow.Preset(0f, 8f, 24f, Color.withAlpha(0xFF000000, 0x59)));

        Glow glow = new Glow(
            new Glow.Preset(6f,  Color.withAlpha(p.accent(), 0x1A)),   // subtle ~10%
            new Glow.Preset(10f, Color.withAlpha(p.accent(), 0x2E)));  // active ~18%

        Elevation elevation = new Elevation(
            new Elevation.Level(surface.bg1(),     0, new Shadow.Preset(0f, 0f, 0f, 0), null),     // level0: no border/shadow
            new Elevation.Level(surface.surface(), border.defaultColor(), shadow.sm(), null),
            new Elevation.Level(surface.bg2(),     border.defaultColor(), shadow.md(), null),
            new Elevation.Level(surface.bg2(),     border.strong(),       shadow.lg(), glow.subtle()));

        Motion motion = new Motion(
            new Motion.Durations(0f, 0.12f, 0.20f, 0.32f),
            new Motion.Easings(Curves.STANDARD, Curves.DECELERATE, Curves.ACCELERATE, Curves.LINEAR));

        return new Theme(p, radius, spacing, type, surface, accent, border, shadow, glow, elevation, motion);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.theme.TokensTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/theme/ src/test/java/com/club/ui/theme/TokensTest.java
git commit -m "feat(ui): token system — records + ClubDark + Tokens facade [M2.1]"
```

---

### Task 4: Component base

**Files:**
- Create: `src/main/java/com/club/ui/component/Component.java`
- Test: `src/test/java/com/club/ui/component/ComponentTest.java`

**Interfaces:**
- Consumes: `com.club.ui.layout.Size`, `com.club.ui.UiContext`.
- Produces: `abstract class Component` with protected `float x,y,w,h`; public `boolean enabled,visible`;
  protected `boolean hovered,pressed,focused`; `Size measure(float,float)` (default `new Size(0,0)`);
  `void layout(float,float,float,float)`; abstract `void render(UiContext)`; input methods (all default
  no-op/false); `boolean contains(double,double)`; test accessors `isHovered()/isPressed()/isFocused()`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentTest {
    static class Probe extends Component {
        @Override public Size measure(float aw, float ah) { return new Size(40, 12); }
        @Override public void render(UiContext ctx) { }
    }
    @Test void layoutAssignsBounds() {
        Probe p = new Probe();
        p.layout(5, 7, 40, 12);
        assertTrue(p.contains(5, 7));
        assertTrue(p.contains(44.9, 18.9));
        assertFalse(p.contains(45, 19));   // exclusive right/bottom
        assertFalse(p.contains(4, 7));
    }
    @Test void measureReturnsDesired() {
        assertEquals(40, new Probe().measure(100, 100).w());
    }
    @Test void inputDefaultsAreInert() {
        Probe p = new Probe();
        assertFalse(p.mouseClicked(0, 0, 0));
        assertFalse(p.keyPressed(0, 0, 0));
        assertFalse(p.charTyped('a', 0));
        assertFalse(p.mouseScrolled(0, 0, 1));
        assertTrue(p.enabled); assertTrue(p.visible);
        assertFalse(p.isHovered());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.component.ComponentTest"`
Expected: FAIL — `Component` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// Component.java
package com.club.ui.component;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;

/** Position-passive UI element. Bounds are assigned by layout(); render() only reads them. */
public abstract class Component {
    protected float x, y, w, h;
    public boolean enabled = true, visible = true;
    protected boolean hovered, pressed, focused;

    /** Intrinsic desired size given available space. Leaf widgets override. */
    public Size measure(float availW, float availH) { return new Size(0, 0); }

    /** Assigns final bounds. Called by the parent container — never self-invoked for positioning. */
    public void layout(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }

    public abstract void render(UiContext ctx);

    public boolean mouseClicked(double mx, double my, int button)     { return false; }
    public boolean mouseReleased(double mx, double my, int button)    { return false; }
    public void    mouseMoved(double mx, double my)                   { }
    public boolean mouseScrolled(double mx, double my, double amount) { return false; }
    public boolean keyPressed(int key, int scan, int mods)           { return false; }
    public boolean charTyped(char ch, int mods)                       { return false; }

    public boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public boolean isHovered() { return hovered; }
    public boolean isPressed() { return pressed; }
    public boolean isFocused() { return focused; }

    public float xLeft()  { return x; }
    public float yTop()   { return y; }
    public float width()  { return w; }
    public float height() { return h; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.component.ComponentTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/component/Component.java src/test/java/com/club/ui/component/ComponentTest.java
git commit -m "feat(ui): Component base (position-passive, measure/layout/input) [M2.1]"
```

---

### Task 5: Container base

**Files:**
- Create: `src/main/java/com/club/ui/component/Container.java`
- Test: `src/test/java/com/club/ui/component/ContainerTest.java`

**Interfaces:**
- Consumes: `Component`, `com.club.ui.UiContext`.
- Produces: `abstract class Container extends Component` with `protected final List<Component> children`;
  `List<Component> children()`; `protected void addChild(Component)`; overrides `render` (draws visible children),
  `mouseClicked`/`mouseScrolled` (reverse z-order hit-test, consume-on-true), `mouseMoved` (sets each child's
  `hovered` + forwards). Top-of-stack = last in `children`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerTest {
    static class ClickProbe extends Component {
        final boolean consume; int clicks;
        ClickProbe(boolean consume) { this.consume = consume; }
        @Override public void render(UiContext ctx) { }
        @Override public boolean mouseClicked(double mx, double my, int b) { clicks++; return consume; }
    }
    static class Box extends Container {
        void add(Component c) { addChild(c); }
    }
    @Test void clickRoutesToTopmostContainingChild() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true);  a.layout(0, 0, 50, 50);
        ClickProbe b = new ClickProbe(true);  b.layout(0, 0, 50, 50); // overlaps a, added later → on top
        box.add(a); box.add(b);
        assertTrue(box.mouseClicked(10, 10, 0));
        assertEquals(1, b.clicks); assertEquals(0, a.clicks);  // topmost consumed
    }
    @Test void clickFallsThroughWhenNotConsumed() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true);  a.layout(0, 0, 50, 50);
        ClickProbe b = new ClickProbe(false); b.layout(0, 0, 50, 50);
        box.add(a); box.add(b);
        assertTrue(box.mouseClicked(10, 10, 0));
        assertEquals(1, b.clicks); assertEquals(1, a.clicks);  // b declined → a got it
    }
    @Test void clickMissesOutsideChild() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true); a.layout(0, 0, 20, 20);
        box.add(a);
        assertFalse(box.mouseClicked(50, 50, 0));
        assertEquals(0, a.clicks);
    }
    @Test void mouseMovedSetsHover() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true); a.layout(0, 0, 20, 20);
        ClickProbe b = new ClickProbe(true); b.layout(50, 50, 20, 20);
        box.add(a); box.add(b);
        box.mouseMoved(10, 10);
        assertTrue(a.isHovered()); assertFalse(b.isHovered());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.component.ContainerTest"`
Expected: FAIL — `Container` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// Container.java
package com.club.ui.component;
import com.club.ui.UiContext;
import java.util.ArrayList;
import java.util.List;

/** Holds and dispatches to children. Subclasses (layout/UI containers) decide how children are laid out. */
public abstract class Container extends Component {
    protected final List<Component> children = new ArrayList<>();

    protected void addChild(Component c) { children.add(c); }
    public List<Component> children() { return children; }

    @Override public void render(UiContext ctx) {
        for (int i = 0; i < children.size(); i++) {
            Component c = children.get(i);
            if (c.visible) c.render(ctx);
        }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Component c = children.get(i);
            if (c.visible && c.enabled && c.contains(mx, my) && c.mouseClicked(mx, my, button)) return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Component c = children.get(i);
            if (c.visible && c.contains(mx, my) && c.mouseScrolled(mx, my, amount)) return true;
        }
        return false;
    }
    @Override public void mouseMoved(double mx, double my) {
        for (int i = 0; i < children.size(); i++) {
            Component c = children.get(i);
            c.hovered = c.visible && c.contains(mx, my);
            c.mouseMoved(mx, my);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.component.ContainerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/component/Container.java src/test/java/com/club/ui/component/ContainerTest.java
git commit -m "feat(ui): Container base — children + input dispatch/hit-test [M2.1]"
```

---

### Task 6: Spacer

**Files:**
- Create: `src/main/java/com/club/ui/layout/Spacer.java`
- Test: `src/test/java/com/club/ui/layout/SpacerTest.java`

**Interfaces:**
- Consumes: `Component`, `Sizing`, `Size`, `UiContext`.
- Produces: `final class Spacer extends Component`; statics `fixed(float px)`, `fill()`, `weight(float)`;
  `float length()`, `Sizing sizing()`; `measure()` returns `Size(0,0)`; `render()` no-op. (Spacer is a layout
  primitive; `Linear` reads `length()`/`sizing()` directly — the one documented exception to "parent sets sizing".)

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.layout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpacerTest {
    @Test void fixedCarriesLength() {
        Spacer s = Spacer.fixed(8);
        assertEquals(8, s.length());
        assertTrue(s.sizing() instanceof Sizing.Fixed);
    }
    @Test void fillAndWeight() {
        assertTrue(Spacer.fill().sizing() instanceof Sizing.Fill);
        Spacer w = Spacer.weight(3);
        assertTrue(w.sizing() instanceof Sizing.Weight);
        assertEquals(3f, ((Sizing.Weight) w.sizing()).value());
        assertEquals(0, w.length());
    }
    @Test void measureIsZero() { assertEquals(0, Spacer.fixed(8).measure(100, 100).w()); }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.SpacerTest"`
Expected: FAIL — `Spacer` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// Spacer.java
package com.club.ui.layout;
import com.club.ui.UiContext;
import com.club.ui.component.Component;

public final class Spacer extends Component {
    private final float length;
    private final Sizing sizing;
    private Spacer(float length, Sizing sizing) { this.length = length; this.sizing = sizing; }
    public static Spacer fixed(float px)  { return new Spacer(px, Sizing.fixed()); }
    public static Spacer fill()           { return new Spacer(0f, Sizing.fill()); }
    public static Spacer weight(float w)  { return new Spacer(0f, Sizing.weight(w)); }
    public float length()  { return length; }
    public Sizing sizing() { return sizing; }
    @Override public Size measure(float availW, float availH) { return new Size(0, 0); }
    @Override public void render(UiContext ctx) { }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.SpacerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/layout/Spacer.java src/test/java/com/club/ui/layout/SpacerTest.java
git commit -m "feat(ui): Spacer layout primitive (fixed/fill/weight) [M2.1]"
```

---

### Task 7: Linear engine + Column + Row

**Files:**
- Create: `src/main/java/com/club/ui/layout/Linear.java`
- Create: `src/main/java/com/club/ui/layout/Column.java`
- Create: `src/main/java/com/club/ui/layout/Row.java`
- Test: `src/test/java/com/club/ui/layout/LinearTest.java`

**Interfaces:**
- Consumes: `Container`, `Component`, `Sizing`, `Insets`, `CrossAlign`, `MainAlign`, `Spacer`, `Size`.
- Produces: `abstract class Linear extends Container` (axis-generic measure/layout; **package-private** internal
  engine — consumers use Column/Row); `public final class Column extends Linear` and `public final class Row extends
  Linear`. Public fluent API per concrete type: `padding(Insets)`,
  `gap(float)`, `crossAlign(CrossAlign)`, `mainAlign(MainAlign)`, `add(Component)`, `add(Component, Sizing)`.
  `add(child)` defaults to `Sizing.fixed()` unless `child instanceof Spacer` (then uses `spacer.sizing()`).
  Fill = Weight(1) for distribution. STRETCH stretches cross extent to inner cross size; else child keeps its
  measured cross extent positioned by CrossAlign. MainAlign offsets the block only when there is no flex child.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.layout;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinearTest {
    /** Fixed-size leaf: measures to (w,h). */
    static class Tile extends Component {
        final float dw, dh;
        Tile(float dw, float dh) { this.dw = dw; this.dh = dh; }
        @Override public Size measure(float aw, float ah) { return new Size(dw, dh); }
        @Override public void render(UiContext ctx) { }
    }
    @Test void columnStacksFixedWithGapAndPadding() {
        Column col = new Column().padding(Insets.all(10)).gap(5).crossAlign(CrossAlign.START);
        Tile a = new Tile(40, 12), b = new Tile(40, 20);
        col.add(a); col.add(b);
        col.layout(0, 0, 100, 200);
        assertEquals(10, a.yTop()); assertEquals(10, a.xLeft());          // padding
        assertEquals(10 + 12 + 5, b.yTop());                              // a.h + gap
        assertEquals(12, a.height()); assertEquals(20, b.height());       // fixed keep measured
    }
    @Test void fillTakesLeftover() {
        Column col = new Column().padding(Insets.ZERO).gap(0);
        Tile fixed = new Tile(40, 30);
        Tile fill  = new Tile(40, 10);
        col.add(fixed);
        col.add(fill, Sizing.fill());
        col.layout(0, 0, 100, 100);
        assertEquals(30, fixed.height());
        assertEquals(70, fill.height());                                 // 100 - 30
        assertEquals(30, fill.yTop());
    }
    @Test void weightSplitsLeftoverProportionally() {
        Column col = new Column();
        Tile one = new Tile(10, 0), two = new Tile(10, 0);
        col.add(one, Sizing.weight(1));
        col.add(two, Sizing.weight(3));
        col.layout(0, 0, 100, 80);
        assertEquals(20, one.height());                                  // 1/4 of 80
        assertEquals(60, two.height());                                  // 3/4 of 80
    }
    @Test void spacerFillPushesNextToEnd() {
        Column col = new Column();
        Tile top = new Tile(10, 10), bottom = new Tile(10, 10);
        col.add(top);
        col.add(Spacer.fill());
        col.add(bottom);
        col.layout(0, 0, 50, 100);
        assertEquals(0, top.yTop());
        assertEquals(90, bottom.yTop());                                 // pushed to bottom
    }
    @Test void crossAlignStretchAndCenter() {
        Column stretch = new Column().crossAlign(CrossAlign.STRETCH);
        Tile t1 = new Tile(30, 10); stretch.add(t1);
        stretch.layout(0, 0, 100, 100);
        assertEquals(0, t1.xLeft()); assertEquals(100, t1.width());      // stretched to inner width

        Column center = new Column().crossAlign(CrossAlign.CENTER);
        Tile t2 = new Tile(30, 10); center.add(t2);
        center.layout(0, 0, 100, 100);
        assertEquals(35, t2.xLeft()); assertEquals(30, t2.width());      // (100-30)/2
    }
    @Test void mainAlignCenterOffsetsBlockWhenNoFlex() {
        Column col = new Column().mainAlign(MainAlign.CENTER);
        Tile a = new Tile(10, 20); col.add(a);
        col.layout(0, 0, 50, 100);
        assertEquals(40, a.yTop());                                      // (100-20)/2
    }
    @Test void rowIsHorizontalTranspose() {
        Row row = new Row().padding(Insets.ZERO).gap(5).crossAlign(CrossAlign.START);
        Tile a = new Tile(12, 40), b = new Tile(20, 40);
        row.add(a); row.add(b);
        row.layout(0, 0, 200, 100);
        assertEquals(0, a.xLeft());
        assertEquals(12 + 5, b.xLeft());                                 // a.w + gap
    }
}
```

> The test uses the `xLeft()/yTop()/width()/height()` read-only accessors added to `Component` in Task 4.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.LinearTest"`
Expected: FAIL — `Column`/`Row`/`Linear` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// Linear.java
package com.club.ui.layout;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import java.util.ArrayList;
import java.util.List;

/** Axis-generic linear layout (package-private internal engine — external consumers use Column/Row). */
abstract class Linear extends Container {
    private final boolean horizontal;
    private final List<Sizing> sizings = new ArrayList<>();
    private Insets padding = Insets.ZERO;
    private float gap = 0f;
    private CrossAlign crossAlign = CrossAlign.START;
    private MainAlign mainAlign = MainAlign.START;

    // reused layout scratch (alloc-free across frames once child count is stable)
    private float[] mainExt = new float[8];
    private float[] crossDes = new float[8];

    protected Linear(boolean horizontal) { this.horizontal = horizontal; }

    protected void setPadding(Insets p)    { this.padding = p; }
    protected void setGap(float g)         { this.gap = g; }
    protected void setCrossAlign(CrossAlign a) { this.crossAlign = a; }
    protected void setMainAlign(MainAlign a)   { this.mainAlign = a; }
    protected void addItem(Component c, Sizing s) {
        Sizing eff = (c instanceof Spacer sp) ? sp.sizing() : s;
        addChild(c);
        sizings.add(eff);
    }
    protected void addItem(Component c) { addItem(c, Sizing.fixed()); }

    private float mainOf(float w, float h) { return horizontal ? w : h; }
    private float crossOf(float w, float h) { return horizontal ? h : w; }

    @Override public Size measure(float availW, float availH) {
        float innerW = availW - padding.horizontal(), innerH = availH - padding.vertical();
        float mainSum = 0f, crossMax = 0f; int n = children.size();
        for (int i = 0; i < n; i++) {
            Component c = children.get(i);
            float len; float cross;
            if (c instanceof Spacer sp && sp.sizing() instanceof Sizing.Fixed) { len = sp.length(); cross = 0; }
            else { Size s = c.measure(innerW, innerH); len = mainOf(s.w(), s.h()); cross = crossOf(s.w(), s.h()); }
            mainSum += len; crossMax = Math.max(crossMax, cross);
        }
        mainSum += gap * Math.max(0, n - 1);
        float mainTotal = mainSum + (horizontal ? padding.horizontal() : padding.vertical());
        float crossTotal = crossMax + (horizontal ? padding.vertical() : padding.horizontal());
        return horizontal ? new Size(mainTotal, crossTotal) : new Size(crossTotal, mainTotal);
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        int n = children.size();
        if (n == 0) return;
        ensureScratch(n);
        float innerX = x + padding.left(), innerY = y + padding.top();
        float innerW = w - padding.horizontal(), innerH = h - padding.vertical();
        float mainAvail = horizontal ? innerW : innerH;
        float crossAvail = horizontal ? innerH : innerW;

        // pass 1: measure, gather fixed main, total weight (Fill counts as weight 1)
        float fixedMain = 0f, totalWeight = 0f;
        for (int i = 0; i < n; i++) {
            Component c = children.get(i);
            Sizing s = sizings.get(i);
            if (s instanceof Sizing.Fixed) {
                float len;
                if (c instanceof Spacer sp) { len = sp.length(); crossDes[i] = 0f; }
                else { Size m = c.measure(innerW, innerH); len = mainOf(m.w(), m.h()); crossDes[i] = crossOf(m.w(), m.h()); }
                mainExt[i] = len; fixedMain += len;
            } else {
                float weight = (s instanceof Sizing.Weight wt) ? wt.value() : 1f;
                totalWeight += weight;
                mainExt[i] = -weight;                       // marker: resolve after leftover known
                Size m = c.measure(innerW, innerH);
                crossDes[i] = (c instanceof Spacer) ? 0f : crossOf(m.w(), m.h());
            }
        }
        float totalGap = gap * (n - 1);
        float leftover = Math.max(0f, mainAvail - fixedMain - totalGap);
        for (int i = 0; i < n; i++) if (mainExt[i] < 0f) mainExt[i] = leftover * (-mainExt[i]) / totalWeight;

        // pass 2: main-axis start offset (only meaningful when nothing flexes)
        float cursor = horizontal ? innerX : innerY;
        if (totalWeight == 0f) {
            float used = fixedMain + totalGap;
            if (mainAlign == MainAlign.CENTER) cursor += (mainAvail - used) / 2f;
            else if (mainAlign == MainAlign.END) cursor += (mainAvail - used);
        }

        // pass 3: place
        for (int i = 0; i < n; i++) {
            Component c = children.get(i);
            float mext = mainExt[i];
            float cext = (crossAlign == CrossAlign.STRETCH) ? crossAvail : crossDes[i];
            float crossStart = horizontal ? innerY : innerX;
            float coff;
            switch (crossAlign) {
                case CENTER  -> coff = (crossAvail - cext) / 2f;
                case END     -> coff = (crossAvail - cext);
                default      -> coff = 0f;                  // START and STRETCH
            }
            float cpos = crossStart + coff;
            if (horizontal) c.layout(cursor, cpos, mext, cext);
            else            c.layout(cpos, cursor, cext, mext);
            cursor += mext + gap;
        }
    }

    private void ensureScratch(int n) {
        if (mainExt.length < n) { mainExt = new float[n]; crossDes = new float[n]; }
    }
}
```
```java
// Column.java
package com.club.ui.layout;
import com.club.ui.component.Component;
public final class Column extends Linear {
    public Column() { super(false); }
    public Column padding(Insets p)        { setPadding(p); return this; }
    public Column gap(float g)             { setGap(g); return this; }
    public Column crossAlign(CrossAlign a) { setCrossAlign(a); return this; }
    public Column mainAlign(MainAlign a)   { setMainAlign(a); return this; }
    public Column add(Component c)             { addItem(c); return this; }
    public Column add(Component c, Sizing s)   { addItem(c, s); return this; }
}
```
```java
// Row.java
package com.club.ui.layout;
import com.club.ui.component.Component;
public final class Row extends Linear {
    public Row() { super(true); }
    public Row padding(Insets p)        { setPadding(p); return this; }
    public Row gap(float g)             { setGap(g); return this; }
    public Row crossAlign(CrossAlign a) { setCrossAlign(a); return this; }
    public Row mainAlign(MainAlign a)   { setMainAlign(a); return this; }
    public Row add(Component c)             { addItem(c); return this; }
    public Row add(Component c, Sizing s)   { addItem(c, s); return this; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.LinearTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/layout/Linear.java src/main/java/com/club/ui/layout/Column.java src/main/java/com/club/ui/layout/Row.java src/test/java/com/club/ui/layout/LinearTest.java
git commit -m "feat(ui): hybrid linear layout — Linear/Column/Row (Fixed/Fill/Weight) [M2.1]"
```

---

### Task 8: Stack

**Files:**
- Create: `src/main/java/com/club/ui/layout/Stack.java`
- Test: `src/test/java/com/club/ui/layout/StackTest.java`

**Interfaces:**
- Consumes: `Container`, `Component`, `Anchor`, `Size`.
- Produces: `final class Stack extends Container`; `Stack add(Component, Anchor)`; overlays children, each placed
  at its measured size, positioned within the Stack's bounds by its `Anchor`. (Dispatch/render inherited.)

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.layout;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StackTest {
    static class Tile extends Component {
        final float dw, dh;
        Tile(float dw, float dh) { this.dw = dw; this.dh = dh; }
        @Override public Size measure(float aw, float ah) { return new Size(dw, dh); }
        @Override public void render(UiContext ctx) { }
    }
    @Test void anchorsPlaceChildren() {
        Stack st = new Stack();
        Tile tl = new Tile(10, 10), center = new Tile(20, 20), br = new Tile(10, 10);
        st.add(tl, Anchor.TOP_LEFT);
        st.add(center, Anchor.CENTER);
        st.add(br, Anchor.BOTTOM_RIGHT);
        st.layout(0, 0, 100, 100);
        assertEquals(0, tl.xLeft());   assertEquals(0, tl.yTop());
        assertEquals(40, center.xLeft()); assertEquals(40, center.yTop());   // (100-20)/2
        assertEquals(90, br.xLeft());  assertEquals(90, br.yTop());          // 100-10
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.StackTest"`
Expected: FAIL — `Stack` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// Stack.java
package com.club.ui.layout;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import java.util.ArrayList;
import java.util.List;

/** Overlays children; each is placed at its measured size, anchored within the Stack bounds. */
public final class Stack extends Container {
    private final List<Anchor> anchors = new ArrayList<>();
    public Stack add(Component c, Anchor a) { addChild(c); anchors.add(a); return this; }

    @Override public Size measure(float availW, float availH) {
        float mw = 0, mh = 0;
        for (Component c : children) { Size s = c.measure(availW, availH); mw = Math.max(mw, s.w()); mh = Math.max(mh, s.h()); }
        return new Size(mw, mh);
    }
    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        for (int i = 0; i < children.size(); i++) {
            Component c = children.get(i);
            Anchor a = anchors.get(i);
            Size s = c.measure(w, h);
            float cw = s.w(), ch = s.h();
            float cx = x + hFactor(a) * (w - cw);
            float cy = y + vFactor(a) * (h - ch);
            c.layout(cx, cy, cw, ch);
        }
    }
    private static float hFactor(Anchor a) {
        return switch (a) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> 0f;
            case TOP, CENTER, BOTTOM         -> 0.5f;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> 1f;
        };
    }
    private static float vFactor(Anchor a) {
        return switch (a) {
            case TOP_LEFT, TOP, TOP_RIGHT          -> 0f;
            case LEFT, CENTER, RIGHT               -> 0.5f;
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> 1f;
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.layout.StackTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/layout/Stack.java src/test/java/com/club/ui/layout/StackTest.java
git commit -m "feat(ui): Stack layout (overlay + anchor) [M2.1]"
```

---

### Task 9: FocusManager

**Files:**
- Create: `src/main/java/com/club/ui/component/FocusManager.java`
- Test: `src/test/java/com/club/ui/component/FocusManagerTest.java`

**Interfaces:**
- Consumes: `Component`.
- Produces: `final class FocusManager`; `void register(Component)`, `void clear()`, `Component focused()`,
  `void focus(Component)`, `void next()`, `void previous()`, `boolean keyPressed(int key,int scan,int mods)`,
  `boolean charTyped(char ch,int mods)`, `void clickFocus(double mx,double my)`. Sets each registered component's
  `focused` flag (single focus owner). `next/previous` wrap around the registration order. Key/char events route
  to the focused component (return false if none).

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component;
import com.club.ui.UiContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FocusManagerTest {
    static class KeyProbe extends Component {
        int keys; boolean accept = true;
        @Override public void render(UiContext ctx) { }
        @Override public boolean keyPressed(int k, int s, int m) { keys++; return accept; }
    }
    @Test void nextWrapsAndSetsFlag() {
        FocusManager fm = new FocusManager();
        KeyProbe a = new KeyProbe(), b = new KeyProbe();
        fm.register(a); fm.register(b);
        fm.next(); assertSame(a, fm.focused()); assertTrue(a.isFocused());
        fm.next(); assertSame(b, fm.focused()); assertFalse(a.isFocused());
        fm.next(); assertSame(a, fm.focused());                 // wrap
        fm.previous(); assertSame(b, fm.focused());             // wrap back
    }
    @Test void keyRoutesToFocused() {
        FocusManager fm = new FocusManager();
        KeyProbe a = new KeyProbe(); fm.register(a);
        assertFalse(fm.keyPressed(1, 0, 0));                    // nothing focused
        fm.focus(a);
        assertTrue(fm.keyPressed(1, 0, 0));
        assertEquals(1, a.keys);
    }
    @Test void clickFocusSelectsHit() {
        FocusManager fm = new FocusManager();
        KeyProbe a = new KeyProbe(); a.layout(0, 0, 10, 10);
        KeyProbe b = new KeyProbe(); b.layout(20, 0, 10, 10);
        fm.register(a); fm.register(b);
        fm.clickFocus(25, 5);
        assertSame(b, fm.focused());
        fm.clickFocus(100, 100);                               // miss → clears focus
        assertNull(fm.focused());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.component.FocusManagerTest"`
Expected: FAIL — `FocusManager` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// FocusManager.java
package com.club.ui.component;
import java.util.ArrayList;
import java.util.List;

/** Single focus owner over a flat list of focusable components. Held by the root container. */
public final class FocusManager {
    private final List<Component> focusables = new ArrayList<>();
    private int index = -1;

    public void register(Component c) { focusables.add(c); }
    public void clear() { focusables.clear(); setIndex(-1); }
    public Component focused() { return index < 0 ? null : focusables.get(index); }

    public void focus(Component c) { setIndex(focusables.indexOf(c)); }
    public void next()     { if (!focusables.isEmpty()) setIndex((index + 1 + focusables.size()) % focusables.size()); }
    public void previous() { if (!focusables.isEmpty()) setIndex((index - 1 + focusables.size()) % focusables.size()); }

    public boolean keyPressed(int key, int scan, int mods) {
        Component f = focused();
        return f != null && f.keyPressed(key, scan, mods);
    }
    public boolean charTyped(char ch, int mods) {
        Component f = focused();
        return f != null && f.charTyped(ch, mods);
    }
    public void clickFocus(double mx, double my) {
        for (int i = focusables.size() - 1; i >= 0; i--) {
            Component c = focusables.get(i);
            if (c.visible && c.enabled && c.contains(mx, my)) { setIndex(i); return; }
        }
        setIndex(-1);
    }
    private void setIndex(int i) {
        if (index >= 0 && index < focusables.size()) focusables.get(index).focused = false;
        index = i;
        if (index >= 0) focusables.get(index).focused = true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.component.FocusManagerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/component/FocusManager.java src/test/java/com/club/ui/component/FocusManagerTest.java
git commit -m "feat(ui): FocusManager — tab traversal + click/key focus [M2.1]"
```

---

### Task 10: UiContextImpl

**Files:**
- Create: `src/main/java/com/club/ui/component/UiContextImpl.java`
- Test: `src/test/java/com/club/ui/component/UiContextImplTest.java`

**Interfaces:**
- Consumes: `com.club.ui.UiContext`, `com.club.ui.Ui`.
- Produces: `final class UiContextImpl implements UiContext`; `renderer()`/`text()` delegate to `Ui.renderer()`/
  `Ui.text()`; `time()` returns a settable seconds value; `void setTime(float)`. Held once (not per-frame alloc);
  `setTime` is called once per frame by the owning root.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiContextImplTest {
    @Test void timeIsSettable() {
        UiContextImpl ctx = new UiContextImpl();
        assertEquals(0f, ctx.time(), 1e-6);
        ctx.setTime(1.5f);
        assertEquals(1.5f, ctx.time(), 1e-6);
    }
    @Test void delegatesRendererAndText() {
        UiContextImpl ctx = new UiContextImpl();
        assertNotNull(ctx.renderer());     // Ui falls back to LEGACY without init → non-null
        assertNotNull(ctx.text());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "com.club.ui.component.UiContextImplTest"`
Expected: FAIL — `UiContextImpl` not found.

- [ ] **Step 3: Write minimal implementation**

```java
// UiContextImpl.java
package com.club.ui.component;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.UiText;

/** Concrete render context. Created once by the owning root; setTime() called once per frame. */
public final class UiContextImpl implements UiContext {
    private float time;
    public void setTime(float seconds) { this.time = seconds; }
    @Override public UiRenderer renderer() { return Ui.renderer(); }
    @Override public UiText text()         { return Ui.text(); }
    @Override public float time()          { return time; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests "com.club.ui.component.UiContextImplTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/component/UiContextImpl.java src/test/java/com/club/ui/component/UiContextImplTest.java
git commit -m "feat(ui): UiContextImpl — render context + frame clock [M2.1]"
```

---

### Task 11: Foundation gate — full build + arch-guard

**Files:** none (verification task).

- [ ] **Step 1: Run the full suite**

Run: `.\gradlew.bat test`
Expected: PASS — all M2.1 tests + all pre-existing Stage 1 tests green.

- [ ] **Step 2: Confirm arch-guard still green**

Run: `.\gradlew.bat test --tests "com.club.ui.ArchitectureRuleTest"`
Expected: PASS — no low-level render calls leaked outside `backend/`.

- [ ] **Step 3: Full compile**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Milestone report + checkpoint**

Write the M2.1 Milestone Report (format in `AI_CONTEXT.md`): что сделано · решения · компромиссы · остаточные
риски · charter V2 (4 принципа). Update `docs/project/CURRENT_TASK.md` (M2.1 done → M2.2 next). Update ledgers
`docs/UI-V2-RISKS.md` / `docs/UI-V2-PERF.md` if anything changed. **Stop for user approval before M2.2.**

---

## Self-Review

**Spec coverage (spec §3–6 vs tasks):**
- §3 Tokens (Palette/Surface/Accent/Border/Shadow/Glow/Radius/Spacing/Typography/Elevation/Motion/Theme/Tokens
  facade + ClubDark values §3.8) → Task 3. ✓
- §4 Layout (Size/Sizing/Insets/aligns → T1; Spacer → T6; Column/Row/Linear Fixed/Fill/Weight/gap/padding/
  cross+main align → T7; Stack anchor → T8). ✓
- §5 Component model (Component → T4; Container dispatch/hit-test → T5; FocusManager → T9; UiContextImpl + clock
  → T10; interaction state flags → T4/T5/T9). ✓
- §6 Motion (Easing/Curves/Transition) → T2. ✓
- §8 testing (layout golden, motion, tokens, component logic, arch-guard) → covered across T1–T10 + T11 gate. ✓
- §1.7 Foundation self-contained / no widgets → enforced (no widget tasks). ✓

**Placeholder scan:** the only narrative note is the `level0`/`shadow_none` clarification in Task 3 — it gives the
exact final line (`new Elevation.Level(surface.bg1(), 0, new Shadow.Preset(0f,0f,0f,0), null)`), so it is concrete,
not a placeholder. No TBD/TODO remain.

**Type consistency:** `Size(w,h)`, `Sizing.fixed/fill/weight`, `Insets.horizontal/vertical`, `Border.defaultColor`,
`Shadow.Preset(dx,dy,blur,color)`, `Glow.Preset(size,color)`, `Elevation.Level(surface,border,shadow,glow)`,
`Typography.Role(weight,size,lineHeight)`, `Transition(initial,duration,easing)`/`target(v,now)`/`value(now)`,
`Component.measure/layout/contains/xLeft/yTop/width/height`, `Container.addChild/children`,
`Linear.addItem`, `Spacer.length/sizing`, `FocusManager.register/next/previous/focus/clickFocus/keyPressed` — used
identically in every task that references them. ✓

**Notes for the implementer:**
- `component` ↔ `layout` form one core; `component` uses `layout.Size`, `layout` containers extend
  `component.Container`. This package back-reference is intentional and harmless (no compile cycle issue in Java).
- Spacer carrying its own `sizing()` is the single sanctioned exception to "parent sets sizing" — Spacer is a
  layout primitive, not a UI widget.
- Layout `mainExt/crossDes` scratch arrays are reused fields (no per-frame allocation); they grow only when child
  count exceeds capacity.
