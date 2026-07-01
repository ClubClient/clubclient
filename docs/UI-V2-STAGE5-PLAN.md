# UI V2 — Stage 5 (functional HUD) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Pause between phases for the owner's review** (per the project rule "не переходить к следующему крупному этапу без подтверждения").

**Goal:** Make the V2 HUD genuinely functional — drag, edge/center snap + optional grid-snap, per-element scale, position persistence, and real game data — by porting the proven logic of the legacy `gui/HudEditorScreen` + `hud/*` onto the frozen V2 render/input backend, while the legacy HUD keeps rendering in-world untouched.

**Architecture:** A small reusable HUD framework in `com.club.ui.devhud`: pure static geometry/snap helpers (`HudSnap`, unit-tested), an abstract `HudElement extends Component` (config-bound position/scale, scaled-box geometry, flat-language `paint`), and a `HudCanvas extends Container` that owns selection + grab-offset drag + snap + guides + persistence (mirroring the legacy editor, using the V2 capture model for the popover). The existing dev screens `HudEditorScreen`/`HudPreviewScreen` are rebuilt on this framework; `HudBootstrap` (CLUB_HUD capture) is unchanged. Scale is **baked into geometry** (the V2 `UiRenderer` has no matrix), and elements show **live data when a world/player exists, else representative sample data** (exactly the legacy editor's philosophy).

**Tech stack:** Java 21, Fabric 1.21.1 (Yarn), Gson config, JUnit 5 (`org.junit.jupiter`) for headless pure-logic tests, the frozen `com.club.ui` render stack (`Ui`/`UiContext`/`UiRenderer`/`UiText`), design tokens via `Tokens`.

---

## Global Constraints

Copy these into every task's mental checklist; they are non-negotiable (from the owner + memory `ui-v2-redesign-direction`, `ui-v2-stage5-decisions`):

- **Frozen — do NOT modify:** `UiRenderer`, `UiText`, `UiContext`, `Ui` facade, `Component`/`Container`/`FocusManager` input contract, `WidgetPaint`, design tokens / `ClubDark` values, the existing M2.2 widgets' public API.
- **Renderer limits (confirmed):** `UiRenderer` has **no** matrix/translate/scale and **no** item/sprite/texture draw — only `rect/roundedRect/border/gradient/shadow/glow/line/circle/pushClip/pushRoundedClip/popClip/pushOpacity/popOpacity`. Scale = bake it into coordinates, sizes, and text `size`. Item/effect icons cannot be drawn through the V2 backend.
- **Flat visual language for HUD data:** rects / rounded-rects / circles / axis-lines / text only; state = a flat color (a small dot or a single 2px accent line), **never** tinted text, **never** a gradient on game info. The only "panel" is the editor popover surface.
- **Config:** reuse the existing `ClubConfig.Hud` fields for overlapping elements. Add new fields **only** for a genuinely new element (Info), behind a `version` bump + a `migrate()` branch. `save()` after every mutation (no autosave). File path/format fixed; backward compat mandatory.
- **Coexistence:** the legacy `HudManager`/`ArmorHud`/`PotionHud`/`TargetHud` keep rendering in-world. The V2 HUD lives **only** in its Editor/Preview screens this stage. Do **not** touch `HudManager`/`HudRenderCallback`. Do **not** remove old `gui`/`hud`, `devmenu`, `devhud`, `devgallery`, bootstraps, or the H/J/G/K keys. The in-world switch + scaffolding removal is a separate later stage, after owner approval.
- **No duplication:** reuse legacy data accessors (exposed additively as `public static`), do not re-implement data reads. Reuse the M2.2 widgets (`Toggle`/`Slider`/`Dropdown`/`Button`) for the popover/toolbar — do not add new widget types.
- **No-alloc-in-render:** cache `TextStyle`/`Radii`/strings; rebuild only on state change (see `ClubMenuScreen`/`Slider` for the idiom).
- **No new design decisions** without owner confirmation (see "Open decisions" below).
- **Adaptation note (TDD scope):** pure logic (snap/grid/clamp/box/drag math) is written test-first with headless JUnit (mirroring `SliderTest`/`ScrollAreaTest`). GL render + `Screen` wiring cannot be unit-tested here; those tasks are verified by `compileJava`, the headless `CLUB_HUD=1` screenshot harness, and manual in-game checks. This matches the codebase's established pattern and is the skill-sanctioned "follow the codebase" adaptation.

---

## Resolved decisions (owner-confirmed 2026-06-30)

1. **Armor — DEFERRED (Variant A).** No `DrawContext` hybrid for a single element; keep the V2 render architecture clean. Armor returns as its own stage once V2 has item-render support or an explicit architectural decision is made. Phase 4 is a stub — **do not implement**.
2. **Target — minimalist, exactly as the approved design:** player name + HP + one thin 2px health line (accent, →red < 30%). **No** distance, **no** head/avatar, **no** 3D preview, **no** extra effects.
3. **Stage 5 element set:** **only** Target, Effects (potions), Info (FPS/XYZ) become fully functional (draggable + real-data). Watermark + Crosshair stay static non-draggable decals. Arraylist, Keystrokes, CPS/BPS, Toast, ModuleList are a **separate stage after Stage 5**.
4. **Execution mode: Subagent-Driven.** After each major phase: show the result, list changed files, state what was implemented, and **wait for owner confirmation — never auto-advance**. Review gates after Phase 2 (infrastructure) and Phase 3 (real data).
5. **Hard coexistence rules until Stage 5 is fully done:** do not replace the old HUD, do not switch the in-world render to V2, do not delete legacy HUD, do not delete dev-code, do not run any final project cleanup.

---

## File Structure

**New (all in `src/main/java/com/club/ui/devhud/`):**
- `HudSnap.java` — pure static: edge/center snap, grid snap, clamp. No GL/MC deps. **Unit-tested.**
- `HudElement.java` — abstract `Component`: config-bound position/scale, `scaledBox` geometry, auto-position resolution, abstract `contentSize`/`paint`. Pure geometry is static + **unit-tested.**
- `HudCanvas.java` — `Container`: owns the element list, editor selection + grab-offset drag + snap + guides + persistence; renders elements (+ guides/selection in editor). Drag math is **unit-tested** via a headless subclass.
- `TargetElement.java`, `EffectsElement.java`, `InfoElement.java` — concrete draggable elements (live-or-sample data, flat paint).
- `Decals.java` — static, non-draggable watermark + crosshair paint helpers (ported from `HudView`).

**New tests (`src/test/java/com/club/ui/devhud/`):**
- `HudSnapTest.java`, `HudElementGeomTest.java`, `HudCanvasDragTest.java`.

**Modified:**
- `src/main/java/com/club/ui/devhud/HudEditorScreen.java` — rebuilt on `HudCanvas` (toolbar: grid-snap toggle / Reset / Done; selection popover from M2.2 widgets; full input forwarding).
- `src/main/java/com/club/ui/devhud/HudPreviewScreen.java` — render `HudCanvas` non-editable.
- `src/main/java/com/club/ui/devhud/HudView.java` — **deleted** (its per-element draws move into elements/`Decals`; both screens stop referencing it).
- `src/main/java/com/club/config/ClubConfig.java` — add Info fields (`info`, `infoX`, `infoY`, `infoScale`) + `version = 5` + a `migrate()` `version < 5` branch.
- `src/main/java/com/club/hud/TargetHud.java` — `raycastTarget` private → `public static` (additive; no behavior change).
- `src/main/java/com/club/hud/PotionHud.java` — `effects`, `title`, `time` private → `public static` (additive; no behavior change).

**Untouched (verify still compile/run):** `HudBootstrap.java`, `HudManager.java`, all legacy `hud/*` rendering, `ClubClient.java` (the J key already opens `devhud.HudEditorScreen`).

---

## Phase 0 — Pure snap / grid / clamp core (TDD)

### Task 0.1: `HudSnap` pure helpers

**Files:**
- Create: `src/main/java/com/club/ui/devhud/HudSnap.java`
- Test: `src/test/java/com/club/ui/devhud/HudSnapTest.java`

**Interfaces:**
- Produces: `HudSnap.Snap(int pos, int guide)`; `HudSnap.snapAxis(int pos,int size,int screen) -> Snap`; `HudSnap.snapToGrid(int pos,int step) -> int`; `HudSnap.clampAxis(int pos,int size,int screen) -> int`; constants `NO_GUIDE`, `MARGIN=4`, `THRESHOLD=6`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.devhud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudSnapTest {
    @Test void snapsToLeftMarginWithinThreshold() {
        HudSnap.Snap s = HudSnap.snapAxis(7, 50, 400);   // |7-4|=3 <= 6
        assertEquals(4, s.pos());
        assertEquals(4, s.guide());                       // guide at MARGIN
    }
    @Test void snapsToCenter() {
        HudSnap.Snap s = HudSnap.snapAxis(176, 50, 400);  // centered target = (400-50)/2 = 175
        assertEquals(175, s.pos());
        assertEquals(200, s.guide());                     // guide at screen/2
    }
    @Test void snapsToRightMargin() {
        HudSnap.Snap s = HudSnap.snapAxis(345, 50, 400);  // far target = 400-50-4 = 346
        assertEquals(346, s.pos());
        assertEquals(396, s.guide());                     // guide at screen-MARGIN
    }
    @Test void noSnapWhenFar() {
        HudSnap.Snap s = HudSnap.snapAxis(100, 50, 400);
        assertEquals(100, s.pos());
        assertEquals(HudSnap.NO_GUIDE, s.guide());
    }
    @Test void gridSnapRoundsToNearestStep() {
        assertEquals(16, HudSnap.snapToGrid(14, 8));
        assertEquals(16, HudSnap.snapToGrid(19, 8));
        assertEquals(-8, HudSnap.snapToGrid(-5, 8));
        assertEquals(13, HudSnap.snapToGrid(13, 0));      // step<=0 → unchanged
    }
    @Test void clampKeepsElementOnScreen() {
        assertEquals(0,   HudSnap.clampAxis(-10, 50, 400));
        assertEquals(350, HudSnap.clampAxis(999, 50, 400)); // screen-size
        assertEquals(100, HudSnap.clampAxis(100, 50, 400));
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `.\gradlew.bat test --tests com.club.ui.devhud.HudSnapTest`
Expected: FAIL (cannot resolve `HudSnap`).

- [ ] **Step 3: Implement `HudSnap`**

```java
package com.club.ui.devhud;

/**
 * Pure HUD-editor geometry: edge/center magnetism, grid snap, on-screen clamp.
 * No GL/Minecraft deps — unit-tested headlessly (cf. Slider.quantize / ScrollArea.clampOffset).
 * Ports the legacy gui/HudEditorScreen snapX/snapY semantics (targets {MARGIN, centered, far}, THRESHOLD).
 */
public final class HudSnap {
    private HudSnap() {}

    public static final int NO_GUIDE = Integer.MIN_VALUE;
    public static final int MARGIN = 4, THRESHOLD = 6;

    /** Snapped top-left + the guide-line coord to draw (NO_GUIDE if no snap). */
    public record Snap(int pos, int guide) {}

    /** Edge/center magnetism on one axis. First match wins (left, centered, far). */
    public static Snap snapAxis(int pos, int size, int screen) {
        int[] targets = { MARGIN, (screen - size) / 2, screen - size - MARGIN };
        int[] guides  = { MARGIN, screen / 2, screen - MARGIN };
        for (int i = 0; i < targets.length; i++)
            if (Math.abs(pos - targets[i]) <= THRESHOLD) return new Snap(targets[i], guides[i]);
        return new Snap(pos, NO_GUIDE);
    }

    /** Snap to the nearest multiple of {@code step} (step <= 0 → unchanged). */
    public static int snapToGrid(int pos, int step) {
        return step > 0 ? Math.round((float) pos / step) * step : pos;
    }

    /** Clamp a top-left coord so a {@code size}-wide element stays fully within {@code screen}. */
    public static int clampAxis(int pos, int size, int screen) {
        return Math.max(0, Math.min(pos, screen - size));
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `.\gradlew.bat test --tests com.club.ui.devhud.HudSnapTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/devhud/HudSnap.java src/test/java/com/club/ui/devhud/HudSnapTest.java
git commit -m "feat(ui): HudSnap pure edge/center + grid snap + clamp (TDD) [Stage5]"
```

---

## Phase 1 — `HudElement` model + box geometry

### Task 1.1: Abstract `HudElement` + static `scaledBox` (TDD on geometry)

**Files:**
- Create: `src/main/java/com/club/ui/devhud/HudElement.java`
- Test: `src/test/java/com/club/ui/devhud/HudElementGeomTest.java`

**Interfaces:**
- Produces:
  - `abstract class HudElement extends Component` with `final String id`.
  - Config binding (abstract): `int cfgX()`, `int cfgY()`, `void cfgX(int)`, `void cfgY(int)`, `float cfgScale()`, `boolean cfgEnabled()`.
  - Auto-position (overridable, default `-1` = none): `int autoX(MinecraftClient mc)`, `int autoY(MinecraftClient mc)`.
  - Content size (abstract): `int[] contentSize(MinecraftClient mc, boolean live)` → unscaled `{w,h}`.
  - Paint (abstract): `void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float scale, boolean live)`.
  - Geometry: `static int[] scaledBox(int x,int y,int cw,int ch,float scale)`; instance `int resolveX(MinecraftClient)`, `int resolveY(MinecraftClient)`, `int[] box(MinecraftClient mc)`, `void layoutFromConfig(MinecraftClient mc)`.
- Consumes: `HudSnap` (later, in the canvas), the M2.2 `Component` base.

- [ ] **Step 1: Write the failing test** (pure geometry only — no MC needed)

```java
package com.club.ui.devhud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudElementGeomTest {
    @Test void scaledBoxAppliesScaleAndKeepsOrigin() {
        int[] b = HudElement.scaledBox(30, 40, 100, 20, 1.5f);
        assertArrayEquals(new int[]{30, 40, 150, 30}, b);
    }
    @Test void scaledBoxFloorsToEightPx() {
        int[] b = HudElement.scaledBox(0, 0, 2, 2, 0.5f);   // 2*0.5=1 → floored to 8
        assertArrayEquals(new int[]{0, 0, 8, 8}, b);
    }
    @Test void scaledBoxRoundsHalfUp() {
        int[] b = HudElement.scaledBox(0, 0, 15, 15, 1.1f); // 16.5 → 17 (Math.round)
        assertArrayEquals(new int[]{0, 0, 17, 17}, b);
    }
}
```

- [ ] **Step 2: Run, verify it fails**

Run: `.\gradlew.bat test --tests com.club.ui.devhud.HudElementGeomTest`
Expected: FAIL (cannot resolve `HudElement`).

- [ ] **Step 3: Implement `HudElement`**

```java
package com.club.ui.devhud;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import net.minecraft.client.MinecraftClient;

/**
 * One positionable HUD element on the V2 stack. Bounds are the element's scaled box at its configured
 * top-left; the canvas lays elements out from config every frame so external edits / resizes reflect.
 * Scale is baked into geometry (the V2 renderer has no matrix). Concrete elements supply config binding,
 * content size, and a flat-language paint. Mirrors the legacy hud/* size()+scaledBox() contract.
 */
public abstract class HudElement extends Component {
    public final String id;
    protected HudElement(String id) { this.id = id; }

    // --- config binding (each concrete element wires these to ClubConfig.Hud) ---
    public abstract int   cfgX();
    public abstract int   cfgY();
    public abstract void  cfgX(int v);
    public abstract void  cfgY(int v);
    public abstract float cfgScale();
    public abstract boolean cfgEnabled();

    // --- auto-position (default: none; e.g. TargetElement centers on the crosshair) ---
    public int autoX(MinecraftClient mc) { return -1; }
    public int autoY(MinecraftClient mc) { return -1; }

    // --- content + paint (concrete elements implement) ---
    /** Unscaled {w,h} of the content for the given data mode (live vs representative sample). */
    public abstract int[] contentSize(MinecraftClient mc, boolean live);
    /** Draw flat-language content with its top-left at (ox,oy), every dimension multiplied by scale. */
    public abstract void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float scale, boolean live);

    // --- geometry (pure; unit-tested) ---
    /** {x, y, max(8,round(cw*scale)), max(8,round(ch*scale))} — ports legacy HudEditorScreen.scaledBox. */
    public static int[] scaledBox(int x, int y, int cw, int ch, float scale) {
        return new int[]{ x, y, Math.max(8, Math.round(cw * scale)), Math.max(8, Math.round(ch * scale)) };
    }

    public int resolveX(MinecraftClient mc) { int x = cfgX(); if (x >= 0) return x; int a = autoX(mc); return a >= 0 ? a : 0; }
    public int resolveY(MinecraftClient mc) { int y = cfgY(); if (y >= 0) return y; int a = autoY(mc); return a >= 0 ? a : 0; }

    /** Scaled box at the resolved position, sized from the data mode that will actually be shown. */
    public int[] box(MinecraftClient mc) {
        boolean live = live(mc);
        int[] cs = contentSize(mc, live);
        return scaledBox(resolveX(mc), resolveY(mc), cs[0], cs[1], cfgScale());
    }

    /** Assign Component bounds from the current config (called by the canvas each frame). */
    public void layoutFromConfig(MinecraftClient mc) {
        int[] b = box(mc);
        super.layout(b[0], b[1], b[2], b[3]);
    }

    /** Live data is used when a player exists; otherwise representative sample data (editor on title screen). */
    protected boolean live(MinecraftClient mc) { return mc != null && mc.player != null; }

    @Override public Size measure(float availW, float availH) { return new Size(w, h); }
    @Override public void render(UiContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        paint(ctx, mc, x, y, cfgScale(), live(mc));
    }
}
```

- [ ] **Step 4: Run, verify it passes**

Run: `.\gradlew.bat test --tests com.club.ui.devhud.HudElementGeomTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/devhud/HudElement.java src/test/java/com/club/ui/devhud/HudElementGeomTest.java
git commit -m "feat(ui): HudElement model + scaledBox geometry (TDD) [Stage5]"
```

---

## Phase 2 — `HudCanvas` drag/snap/grid + V2 editor screen

### Task 2.1: `HudCanvas` selection + grab-offset drag + snap + grid + persistence (TDD on drag math)

**Files:**
- Create: `src/main/java/com/club/ui/devhud/HudCanvas.java`
- Test: `src/test/java/com/club/ui/devhud/HudCanvasDragTest.java`

**Interfaces:**
- Consumes: `HudElement`, `HudSnap`, `Container`.
- Produces:
  - `HudCanvas(boolean editor)`; `HudCanvas add(HudElement e)`.
  - `void setScreen(int w, int h)` (the editor passes scaled-GUI dims each frame, so the math is MC-free and testable).
  - `void setGridSnap(boolean on)`, `boolean gridSnap()`, constant `GRID_STEP = 8`.
  - `HudElement selected()`; `void clearSelection()`; `Runnable onSelectionChanged` hook (editor rebuilds the popover).
  - `int guideX()`, `int guideY()` (NO_GUIDE when none).
  - Overrides `mouseClicked/Dragged/Released` (drag) and `render` (paints elements; in editor also guides + selection border).
  - `void layoutFromConfig(MinecraftClient mc)` (lays every element out from config).
  - Persistence via injected `Runnable saver` (defaults to `ClubConfig::save`; tests inject a no-op).

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.devhud;

import com.club.ui.UiContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudCanvasDragTest {
    /** A headless element backed by plain ints (no ClubConfig / MC). */
    static final class FakeElement extends HudElement {
        int x, y; float scale; final int cw, ch;
        FakeElement(String id, int x, int y, float scale, int cw, int ch) { super(id); this.x=x; this.y=y; this.scale=scale; this.cw=cw; this.ch=ch; }
        @Override public int cfgX() { return x; }
        @Override public int cfgY() { return y; }
        @Override public void cfgX(int v) { x = v; }
        @Override public void cfgY(int v) { y = v; }
        @Override public float cfgScale() { return scale; }
        @Override public boolean cfgEnabled() { return true; }
        @Override public int[] contentSize(net.minecraft.client.MinecraftClient mc, boolean live) { return new int[]{cw, ch}; }
        @Override public void paint(UiContext c, net.minecraft.client.MinecraftClient mc, float ox, float oy, float s, boolean live) {}
        @Override protected boolean live(net.minecraft.client.MinecraftClient mc) { return false; } // sample sizing, no MC
        @Override public int[] box(net.minecraft.client.MinecraftClient mc) { return scaledBox(x, y, cw, ch, scale); }
    }

    @Test void dragMovesElementByGrabOffsetAndPersistsOnRelease() {
        FakeElement e = new FakeElement("t", 100, 100, 1f, 50, 20);
        boolean[] saved = {false};
        HudCanvas c = new HudCanvas(true); c.saver(() -> saved[0] = true); c.add(e); c.setScreen(400, 300);
        e.layout(100, 100, 50, 20);
        assertTrue(c.mouseClicked(110, 105, 0));     // press inside (grab offset 10,5)
        c.mouseDragged(210, 155, 0, 0, 0);           // cursor → (210,155) ⇒ top-left (200,150)
        assertEquals(200, e.x); assertEquals(150, e.y);
        assertFalse(saved[0]);                        // not yet
        c.mouseReleased(210, 155, 0);
        assertTrue(saved[0]);                         // drag commits
    }

    @Test void dragSnapsToCenter() {
        FakeElement e = new FakeElement("t", 0, 0, 1f, 50, 20);
        HudCanvas c = new HudCanvas(true); c.saver(() -> {}); c.add(e); c.setScreen(400, 300);
        e.layout(0, 0, 50, 20);
        c.mouseClicked(0, 0, 0);                      // grab offset (0,0)
        c.mouseDragged(176, 5, 0, 0, 0);             // x near centered target 175
        assertEquals(175, e.x);                       // snapped
        assertEquals(200, c.guideX());                // center guide shown
    }

    @Test void gridSnapQuantizesWhenEnabled() {
        FakeElement e = new FakeElement("t", 0, 0, 1f, 50, 20);
        HudCanvas c = new HudCanvas(true); c.saver(() -> {}); c.add(e); c.setScreen(400, 300);
        c.setGridSnap(true);
        e.layout(0, 0, 50, 20);
        c.mouseClicked(0, 0, 0);
        c.mouseDragged(101, 51, 0, 0, 0);            // 101→104? grid 8: round(101/8)*8=104; 51→48
        assertEquals(104, e.x); assertEquals(48, e.y);
    }

    @Test void tapWithoutMoveSelectsElement() {
        FakeElement e = new FakeElement("t", 10, 10, 1f, 50, 20);
        HudCanvas c = new HudCanvas(true); c.saver(() -> {}); c.add(e); c.setScreen(400, 300);
        e.layout(10, 10, 50, 20);
        c.mouseClicked(20, 15, 0);
        c.mouseReleased(20, 15, 0);                   // no drag → select
        assertSame(e, c.selected());
    }
}
```

> Each test constructs the canvas inline and lays the element's bounds explicitly via `e.layout(...)` — no Minecraft context needed (the `FakeElement` overrides `live()`→false and `box()` to avoid `mc`).

- [ ] **Step 2: Run, verify it fails**

Run: `.\gradlew.bat test --tests com.club.ui.devhud.HudCanvasDragTest`
Expected: FAIL (cannot resolve `HudCanvas`).

- [ ] **Step 3: Implement `HudCanvas`**

```java
package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.component.Container;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts HUD elements and, in editor mode, owns selection + grab-offset drag + snap + guides + persistence
 * (centralized exactly like the legacy gui/HudEditorScreen, but on the V2 stack). Screen dims are injected
 * via setScreen() so the drag/snap math is Minecraft-free and unit-tested.
 */
public final class HudCanvas extends Container {
    public static final int GRID_STEP = 8;

    private final boolean editor;
    private final List<HudElement> elements = new ArrayList<>();
    private int screenW, screenH;
    private boolean gridSnap;
    private Runnable saver = ClubConfig::save;
    public Runnable onSelectionChanged = () -> {};

    private HudElement selected, pressed;
    private int grabX, grabY;
    private boolean moved;
    private int guideX = HudSnap.NO_GUIDE, guideY = HudSnap.NO_GUIDE;

    public HudCanvas(boolean editor) { this.editor = editor; }

    public HudCanvas add(HudElement e) { elements.add(e); addChild(e); return this; }
    public void setScreen(int w, int h) { this.screenW = w; this.screenH = h; }
    public void setGridSnap(boolean on) { this.gridSnap = on; }
    public boolean gridSnap() { return gridSnap; }
    public void saver(Runnable r) { this.saver = r; }
    public HudElement selected() { return selected; }
    public void clearSelection() { if (selected != null) { selected = null; onSelectionChanged.run(); } }
    public int guideX() { return guideX; }
    public int guideY() { return guideY; }
    public List<HudElement> elements() { return elements; }

    public void layoutFromConfig(MinecraftClient mc) { for (HudElement e : elements) e.layoutFromConfig(mc); }

    private HudElement elementAt(double mx, double my) {
        for (int i = elements.size() - 1; i >= 0; i--) {              // top-most first
            HudElement e = elements.get(i);
            if (mx >= e.xLeft() - 4 && mx <= e.xLeft() + e.width() + 4
             && my >= e.yTop()  - 4 && my <= e.yTop()  + e.height() + 4) return e;
        }
        return null;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (!editor || button != 0) return false;
        moved = false;
        pressed = elementAt(mx, my);
        if (pressed != null) { grabX = (int) mx - (int) pressed.xLeft(); grabY = (int) my - (int) pressed.yTop(); return true; }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!editor || pressed == null) return false;
        moved = true;
        int w = (int) pressed.width(), h = (int) pressed.height();
        int nx = (int) mx - grabX, ny = (int) my - grabY;

        if (gridSnap) { nx = HudSnap.snapToGrid(nx, GRID_STEP); ny = HudSnap.snapToGrid(ny, GRID_STEP); guideX = guideY = HudSnap.NO_GUIDE; }
        else {
            HudSnap.Snap sx = HudSnap.snapAxis(nx, w, screenW); nx = sx.pos(); guideX = sx.guide();
            HudSnap.Snap sy = HudSnap.snapAxis(ny, h, screenH); ny = sy.pos(); guideY = sy.guide();
        }
        nx = HudSnap.clampAxis(nx, w, screenW);
        ny = HudSnap.clampAxis(ny, h, screenH);
        pressed.cfgX(nx); pressed.cfgY(ny);
        pressed.layout(nx, ny, w, h);            // keep bounds in sync for continued hit-test
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (!editor) return false;
        guideX = guideY = HudSnap.NO_GUIDE;
        boolean handled = false;
        if (pressed != null) {
            if (moved) saver.run();
            else { selected = (selected == pressed) ? null : pressed; onSelectionChanged.run(); }
            handled = true;
        } else if (!moved && selected != null) { selected = null; onSelectionChanged.run(); handled = true; }
        pressed = null; moved = false;
        return handled;
    }

    @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }

    @Override public void render(UiContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (HudElement e : elements) {
            if (!editor && !e.cfgEnabled()) continue;        // preview/in-world hides disabled; editor shows all
            e.render(ctx);
        }
        if (!editor) return;

        // selection border (accent) over the selected element's box
        if (selected != null) {
            float bx = selected.xLeft(), by = selected.yTop(), bw = selected.width(), bh = selected.height();
            ctx.renderer().border(bx - 4, by - 4, bw + 8, bh + 8, Tokens.radius().sm(), 1.5f, Tokens.accent().accent());
        }
        // alignment guides (1px accent, ~0xAA alpha) — ports legacy guideX/guideY
        int g = Color.withAlpha(Tokens.accent().accent(), 0xAA);
        if (guideX != HudSnap.NO_GUIDE) ctx.renderer().rect(guideX, 0, 1, screenH, g);
        if (guideY != HudSnap.NO_GUIDE) ctx.renderer().rect(0, guideY, screenW, 1, g);
    }
}
```

> `HudCanvas` extends `Container`, so `addChild` is available to its `add(...)`; the four behavioral tests are self-contained.

- [ ] **Step 4: Run, verify it passes**

Run: `.\gradlew.bat test --tests com.club.ui.devhud.HudCanvasDragTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/club/ui/devhud/HudCanvas.java src/test/java/com/club/ui/devhud/HudCanvasDragTest.java
git commit -m "feat(ui): HudCanvas selection+grab-drag+snap+grid+persistence (TDD) [Stage5]"
```

### Task 2.2: Concrete elements with sample data + `Decals` (port `HudView` visuals)

**Files:**
- Create: `src/main/java/com/club/ui/devhud/TargetElement.java`, `EffectsElement.java`, `InfoElement.java`, `Decals.java`
- Modify: `src/main/java/com/club/config/ClubConfig.java` (Info fields + v5 migration)

**Interfaces:**
- Consumes: `HudElement`, `ClubConfig.Hud`, `Tokens`, `UiContext`/`UiText`/`UiRenderer`, `Color`.
- Produces: three `HudElement` subclasses bound to config (`potion*` for Effects, `target*` for Target, new `info*` for Info); `Decals.watermark(ctx)` and `Decals.crosshair(ctx,w,h)`.

- [ ] **Step 1: Add Info config fields + v5 migration** (`ClubConfig.java`)

In `class Hud` add after `hideVanillaEffects`:
```java
        // V2 HUD: coordinates/FPS readout (new in v5)
        public boolean info = true;
        public int infoX = 8;
        public int infoY = 120;
        public float infoScale = 1.0f;
```
Change `public int version = 4;` → `public int version = 5;`. In `migrate()` after the `version < 4` block:
```java
        if (version < 5) {
            // new V2 Info element defaults (Gson leaves missing primitives at 0 → would pin to corner)
            hud.info = true;
            hud.infoX = 8; hud.infoY = 120; hud.infoScale = 1.0f;
            version = 5;
            changed = true;
        }
```

- [ ] **Step 2: Verify config still compiles + existing tests pass**

Run: `.\gradlew.bat compileJava test`
Expected: BUILD SUCCESSFUL (no regression in existing suites).

- [ ] **Step 3: Implement `Decals`** (port `HudView` watermark + crosshair verbatim)

```java
package com.club.ui.devhud;

import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

/** Static, non-draggable HUD decals (watermark + crosshair) — ported from the approved HudView mock. */
public final class Decals {
    private Decals() {}
    private static TextStyle st(Typography.Role r, int c) { return TextStyle.of(r.weight(), r.size(), c); }
    private static float tw(String s, Typography.Role r) { return Ui.text().width(s, r.weight(), r.size()); }

    public static void watermark(UiContext ctx) {
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        int hi = Tokens.palette().textHi(), faint = Tokens.palette().textFaint(), accent = Tokens.accent().accent();
        r.roundedRect(18, 20, 8, 8, 2, accent);
        t.draw("CLUB", 32, 16, st(ty.title(), hi));
        t.draw("v2.5", 32 + tw("CLUB", ty.title()) + 8, 19, st(ty.label(), faint));
    }

    public static void crosshair(UiContext ctx, int w, int h) {
        var r = ctx.renderer();
        float cx = w / 2f, cy = h * 0.47f;
        int xh = Color.withAlpha(Tokens.palette().textHi(), 0x99);
        r.rect(cx - 9, cy - 1, 18, 2, xh);
        r.rect(cx - 1, cy - 9, 2, 18, xh);
    }
}
```

- [ ] **Step 4: Implement `EffectsElement`** (port HudView effects block; scale-baked; sample data now, live in Phase 3)

```java
package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Active effects: a column of [chip] Name  Time rows. Pure-vector (no sprite — matches the V2 mock). */
public final class EffectsElement extends HudElement {
    private static final int ROW = 26, CHIP_H = 22, CHIP_W = 120;
    public EffectsElement() { super("effects"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().potionX; }
    @Override public int   cfgY() { return h().potionY; }
    @Override public void  cfgX(int v) { h().potionX = v; }
    @Override public void  cfgY(int v) { h().potionY = v; }
    @Override public float cfgScale() { return h().potionScale; }
    @Override public boolean cfgEnabled() { return h().potions; }

    /** {name,time} rows — sample now; Phase 3 swaps in PotionHud.effects(mc). */
    private String[][] rows(MinecraftClient mc, boolean live) {
        return new String[][]{{"Speed II", "1:24"}, {"Strength I", "0:42"}};
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        int n = rows(mc, live).length;
        return new int[]{ CHIP_W, Math.max(CHIP_H, (n - 1) * ROW + CHIP_H) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        int chip = Color.withAlpha(Tokens.palette().ink0(), 0x8C), sub = Tokens.border().subtle();
        int hi = Tokens.palette().textHi(), mut = Tokens.palette().textMuted();
        float sm = Tokens.radius().sm() * s, cw = CHIP_W * s, ch = CHIP_H * s, lh = ty.label().lineHeight() * s;
        String[][] rows = rows(mc, live);
        for (int i = 0; i < rows.length; i++) {
            float py = oy + i * ROW * s;
            r.roundedRect(ox, py, cw, ch, sm, chip);
            r.border(ox, py, cw, ch, sm, 1, sub);
            t.draw(rows[i][0], ox + 9 * s, py + (ch - lh) / 2f, TextStyle.of(ty.label().weight(), ty.label().size() * s, hi));
            t.draw(rows[i][1], ox + cw - 9 * s, py + (ch - lh) / 2f, TextStyle.of(ty.label().weight(), ty.label().size() * s, mut).align(Align.RIGHT));
        }
    }
}
```

- [ ] **Step 5: Implement `TargetElement`** (name + muted sub-line + 2px HP line; auto-centers; sample now)

```java
package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Entity under the crosshair: name + "<hp> HP" + a 2px HP-fraction line (the only accent). No avatar/distance (per the approved minimalist design). */
public final class TargetElement extends HudElement {
    private static final int CONTENT_W = 150, CONTENT_H = 34;
    public TargetElement() { super("target"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().targetX; }
    @Override public int   cfgY() { return h().targetY; }
    @Override public void  cfgX(int v) { h().targetX = v; }
    @Override public void  cfgY(int v) { h().targetY = v; }
    @Override public float cfgScale() { return h().targetScale; }
    @Override public boolean cfgEnabled() { return h().target; }

    @Override public int autoX(MinecraftClient mc) { return mc != null ? mc.getWindow().getScaledWidth() / 2 + 16 : -1; }
    @Override public int autoY(MinecraftClient mc) { return mc != null ? mc.getWindow().getScaledHeight() / 2 - CONTENT_H / 2 : -1; }

    // {name, hp-subline} — sample now; Phase 3 raycasts via TargetHud.raycastTarget.
    private String name = "Steve_42", sub = "18.6 HP"; private float frac = 0.62f;

    @Override public int[] contentSize(MinecraftClient mc, boolean live) { return new int[]{ CONTENT_W, CONTENT_H }; }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        int hi = Tokens.palette().textHi(), desc = Tokens.palette().textDesc(),
            track = Tokens.surface().surfaceHi(), accent = Tokens.accent().accent(), low = Tokens.palette().stateBad();
        t.draw(name, ox, oy, TextStyle.of(ty.body().weight(), ty.body().size() * s, hi));
        t.draw(sub, ox, oy + 18 * s, TextStyle.of(ty.caption().weight(), ty.caption().size() * s, desc));
        float barY = oy + 30 * s, barW = CONTENT_W * s, barH = 2 * s, rr = 1 * s;
        r.roundedRect(ox, barY, barW, barH, rr, track);
        if (frac > 0) r.roundedRect(ox, barY, barW * frac, barH, rr, frac < 0.30f ? low : accent);
    }
}
```

> `Tokens.palette().stateBad()` — confirm the exact accessor name on `Palette` (values `stateGood/…/stateBad`/`stateLow`) when implementing; the `ClubDark` palette ends `0xFF2ECC71, 0xFFE3C66A, 0xFFE06B6B` (good/warn/bad). Use the bad/low accessor.

- [ ] **Step 6: Implement `InfoElement`** (FPS/XYZ readout; sample now, live in Phase 3)

```java
package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Bottom-left readout: FPS + XYZ (CPS/BPS deferred — need a click tracker). */
public final class InfoElement extends HudElement {
    private static final int CONTENT_W = 160, ROW = 18;
    public InfoElement() { super("info"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().infoX; }
    @Override public int   cfgY() { return h().infoY; }
    @Override public void  cfgX(int v) { h().infoX = v; }
    @Override public void  cfgY(int v) { h().infoY = v; }
    @Override public float cfgScale() { return h().infoScale; }
    @Override public boolean cfgEnabled() { return h().info; }

    /** {label,value,isXYZ} rows — sample now; Phase 3 fills FPS + player XYZ. */
    private String[][] rows(MinecraftClient mc, boolean live) {
        return new String[][]{{"FPS", "240", "0"}, {"XYZ", "128 / 72 / -340", "1"}};
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        return new int[]{ CONTENT_W, rows(mc, live).length * ROW };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text(); Typography ty = Tokens.type();
        int desc = Tokens.palette().textDesc(), hi = Tokens.palette().textHi(), accent = Tokens.accent().accent();
        String[][] rows = rows(mc, live);
        for (int i = 0; i < rows.length; i++) {
            float ry = oy + i * ROW * s; boolean xyz = rows[i][2].equals("1");
            t.draw(rows[i][0], ox, ry, TextStyle.of(ty.label().weight(), ty.label().size() * s, desc));
            t.draw(rows[i][1], ox + 38 * s, ry, TextStyle.of(ty.label().weight(), ty.label().size() * s, xyz ? hi : accent));
        }
    }
}
```

- [ ] **Step 7: Compile**

Run: `.\gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/club/ui/devhud/TargetElement.java src/main/java/com/club/ui/devhud/EffectsElement.java src/main/java/com/club/ui/devhud/InfoElement.java src/main/java/com/club/ui/devhud/Decals.java src/main/java/com/club/config/ClubConfig.java
git commit -m "feat(ui): V2 HUD elements (Target/Effects/Info) + decals + config v5 [Stage5]"
```

### Task 2.3: Rebuild `HudEditorScreen` + `HudPreviewScreen` on the canvas; delete `HudView`

**Files:**
- Modify: `src/main/java/com/club/ui/devhud/HudEditorScreen.java`, `HudPreviewScreen.java`
- Delete: `src/main/java/com/club/ui/devhud/HudView.java`

**Interfaces:**
- Consumes: `HudCanvas`, the three elements, `Decals`, M2.2 `Toggle`/`Slider`/`Dropdown`/`Button`, the `ClubMenuScreen` input-forwarding template, `ClubConfig`.

- [ ] **Step 1: Rebuild `HudPreviewScreen`**

```java
package com.club.ui.devhud;

import com.club.ui.Ui;
import com.club.ui.component.UiContextImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** [DEV HUD — TEMPORARY] In-game HUD preview over a flat dark stand-in scene (non-editable canvas). */
public final class HudPreviewScreen extends Screen {
    private final UiContextImpl uiCtx = new UiContextImpl();
    private final long start = System.nanoTime();
    private final HudCanvas canvas = new HudCanvas(false)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement());

    public HudPreviewScreen() { super(Text.literal("HUD")); }

    @Override public void render(DrawContext dc, int mx, int my, float d) {
        Ui.beginFrame(dc);
        Ui.renderer().rect(0, 0, width, height, 0xFF0A0E15);
        uiCtx.setTime((System.nanoTime() - start) / 1_000_000_000f);
        canvas.setScreen(width, height);
        canvas.layoutFromConfig(MinecraftClient.getInstance());
        Decals.watermark(uiCtx);
        Decals.crosshair(uiCtx, width, height);
        canvas.render(uiCtx);
    }
    @Override public void renderBackground(DrawContext dc, int mx, int my, float d) { }
    @Override public boolean shouldPause() { return false; }
}
```

- [ ] **Step 2: Rebuild `HudEditorScreen`** (toolbar + popover + full input forwarding)

```java
package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.Ui;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.component.FocusManager;
import com.club.ui.component.UiContextImpl;
import com.club.ui.component.widget.Button;
import com.club.ui.component.widget.Dropdown;
import com.club.ui.component.widget.Label;
import com.club.ui.component.widget.Slider;
import com.club.ui.component.widget.Toggle;
import com.club.ui.layout.Column;
import com.club.ui.layout.CrossAlign;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Row;
import com.club.ui.layout.Size;
import com.club.ui.layout.Sizing;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import static org.lwjgl.glfw.GLFW.*;

/**
 * [DEV HUD — TEMPORARY] Functional V2 HUD editor: drag elements (edge/center snap + optional grid),
 * scale/toggle/configure via a selection popover, persist to ClubConfig. Legacy HUD is untouched.
 */
public final class HudEditorScreen extends Screen {
    private final UiContextImpl uiCtx = new UiContextImpl();
    private final FocusManager focus = new FocusManager();
    private final long start = System.nanoTime();

    private final HudCanvas canvas = new HudCanvas(true)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement());
    private final Pane toolbar = new Pane();
    private final Pane popover = new Pane();
    private int popX, popY, popW, popH;
    private boolean hasPopover;
    private TextStyle stTitle, stHint, stPop;

    public HudEditorScreen() { super(Text.literal("HUD Editor")); }
    private ClubConfig.Hud h() { return ClubConfig.get().hud; }

    @Override protected void init() {
        canvas.onSelectionChanged = this::rebuildPopover;
        buildToolbar();
        rebuildPopover();
    }

    private void buildToolbar() {
        toolbar.clear();
        Toggle grid = new Toggle(canvas.gridSnap()).onChange(canvas::setGridSnap);
        grid.layout(150, 9, 40, 22);
        Button reset = new Button("Reset").variant(Button.Variant.GHOST).onClick(this::resetPositions);
        reset.layout(width - 220, 7, 96, 26);
        Button done = new Button("Done").variant(Button.Variant.PRIMARY).onClick(this::close);
        done.layout(width - 116, 7, 96, 26);
        toolbar.add(grid); toolbar.add(reset); toolbar.add(done);
    }

    private void rebuildPopover() {
        popover.clear(); focus.clear(); hasPopover = canvas.selected() != null;
        if (!hasPopover) return;
        HudElement sel = canvas.selected();
        popW = 210;
        int rows = sel instanceof EffectsElement ? 3 : sel instanceof TargetElement ? 3 : 2; // enabled,size,+extra
        popH = 34 + rows * 30 + 10;
        int[] b = { (int) sel.xLeft(), (int) sel.yTop(), (int) sel.width(), (int) sel.height() };
        int px = b[0] + b[2] + 14; if (px + popW > width - 8) px = b[0] - popW - 14;
        popX = Math.max(8, Math.min(px, width - popW - 8));
        popY = Math.max(8, Math.min(b[1] - 4, height - popH - 8));

        int ix = popX + 14, iw = popW - 28, y = popY + 34;
        // Enabled
        Toggle en = new Toggle(sel.cfgEnabled()).onChange(v -> { setEnabled(sel, v); save(); });
        addRow("Enabled", en, ix, iw, y); y += 30; focus.register(en);
        // Size
        Slider size = new Slider(sel.cfgScale(), 0.5f, 2f, 0.05f).onChange(v -> { setScale(sel, v); save(); });
        addRow("Size", size, ix, iw, y); y += 30; focus.register(size);
        // per-type extra
        if (sel instanceof EffectsElement) {
            Dropdown d = new Dropdown(new String[]{"Column", "Row"}, h().potionHorizontal ? 1 : 0)
                    .onChange(i -> { h().potionHorizontal = (i == 1); save(); });
            addRow("Layout", d, ix, iw, y); focus.register(d);
        } else if (sel instanceof TargetElement) {
            Slider range = new Slider(h().targetDistance, 3f, 32f, 1f)
                    .onChange(v -> { h().targetDistance = Math.round(v); save(); });
            addRow("Range", range, ix, iw, y); focus.register(range);
        }
    }

    private final java.util.List<Label> popLabels = new java.util.ArrayList<>();
    private void addRow(String name, Component ctrl, int ix, int iw, int y) {
        Label l = new Label(name, Tokens.type().label()).color(Tokens.palette().textMuted());
        l.layout(ix, y + 4, iw, 16); popLabels.add(l);
        float cw = ctrl.measure(iw, 24).w(); if (cw <= 0 || ctrl instanceof Slider) cw = 96;
        ctrl.layout(ix + iw - cw, y, cw, 24);
        popover.add(ctrl);
    }

    private void setEnabled(HudElement e, boolean v) {
        if (e instanceof EffectsElement) h().potions = v; else if (e instanceof TargetElement) h().target = v; else h().info = v;
    }
    private void setScale(HudElement e, float v) {
        if (e instanceof EffectsElement) h().potionScale = v; else if (e instanceof TargetElement) h().targetScale = v; else h().infoScale = v;
    }
    private void save() { ClubConfig.save(); }

    private void resetPositions() {
        ClubConfig.Hud c = h();
        c.potionX = 8; c.potionY = 70; c.targetX = -1; c.targetY = -1; c.infoX = 8; c.infoY = 120;
        save(); canvas.clearSelection(); rebuildPopover();
    }

    @Override public void render(DrawContext dc, int mx, int my, float d) {
        Ui.beginFrame(dc);
        var r = Ui.renderer(); Typography ty = Tokens.type();
        if (stTitle == null) initStyles();
        r.rect(0, 0, width, height, 0xFF0A0E15);
        uiCtx.setTime((System.nanoTime() - start) / 1_000_000_000f);

        canvas.setScreen(width, height);
        canvas.layoutFromConfig(MinecraftClient.getInstance());
        Decals.watermark(uiCtx); Decals.crosshair(uiCtx, width, height);

        canvas.mouseMoved(mx, my);
        canvas.render(uiCtx);

        // toolbar bar
        float tbH = 40;
        r.rect(0, 0, width, tbH, Tokens.surface().bg2());
        r.rect(0, tbH, width, 1, Tokens.border().defaultColor());
        uiCtx.text().draw("HUD Editor", 18, (tbH - ty.label().lineHeight()) / 2f, stTitle);
        uiCtx.text().draw(canvas.gridSnap() ? "Grid 8px" : "Snap edges", 200, (tbH - ty.label().lineHeight()) / 2f, stHint);
        toolbar.mouseMoved(mx, my); toolbar.render(uiCtx);

        // hint
        uiCtx.text().draw("Drag any element. Click it to edit. Toggle grid-snap in the toolbar.",
                width / 2f, tbH + 8, stHint);

        // popover
        if (hasPopover) {
            r.roundedRect(popX, popY, popW, popH, Tokens.radius().lg(), Tokens.surface().surface());
            r.border(popX, popY, popW, popH, Tokens.radius().lg(), 1, Tokens.border().strong());
            r.roundedRect(popX + 14, popY + 14, 7, 7, 2, Tokens.accent().accent());
            uiCtx.text().draw(titleOf(canvas.selected()), popX + 27, popY + 12, stPop);
            for (Label l : popLabels) l.render(uiCtx);
            popover.mouseMoved(mx, my); popover.render(uiCtx);
        }
    }

    private String titleOf(HudElement e) {
        return e instanceof EffectsElement ? "Effects HUD" : e instanceof TargetElement ? "Target HUD" : "Coordinates HUD";
    }
    private void initStyles() {
        Typography t = Tokens.type();
        stTitle = TextStyle.of(t.title().weight(), t.title().size(), Tokens.palette().textHi());
        stHint  = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textDesc()).align(Align.CENTER);
        stPop   = TextStyle.of(t.body().weight(), t.body().size(), Tokens.palette().textHi());
    }

    @Override public void renderBackground(DrawContext dc, int mx, int my, float d) { }

    // input: toolbar + popover widgets first (capture), then canvas drag
    @Override public boolean mouseClicked(double mx, double my, int b) {
        focus.clickFocus(mx, my);
        if (toolbar.mouseClicked(mx, my, b)) return true;
        if (hasPopover && mx >= popX && mx <= popX + popW && my >= popY && my <= popY + popH) { popover.mouseClicked(mx, my, b); return true; }
        return canvas.mouseClicked(mx, my, b) || super.mouseClicked(mx, my, b);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        boolean h = toolbar.mouseReleased(mx, my, b) | popover.mouseReleased(mx, my, b) | canvas.mouseReleased(mx, my, b);
        return h || super.mouseReleased(mx, my, b);
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        return toolbar.mouseDragged(mx, my, b, dx, dy) || popover.mouseDragged(mx, my, b, dx, dy)
                || canvas.mouseDragged(mx, my, b, dx, dy) || super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean keyPressed(int k, int scan, int mods) {
        if (k == GLFW_KEY_ESCAPE) { close(); return true; }
        return focus.keyPressed(k, scan, mods) || super.keyPressed(k, scan, mods);
    }
    @Override public boolean shouldPause() { return false; }

    /** Free-form container (children positioned by the screen). */
    private static final class Pane extends Container {
        void add(Component c) { addChild(c); }
        void clear() { children.clear(); }
        @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }
    }
}
```

> Confirm the `Label(String, Typography.Role)` constructor + `.color(int)` and `FocusManager.clickFocus/keyPressed/clear/register` signatures against `ClubMenuScreen` (used identically there) when implementing; adjust if the Label ctor differs.

- [ ] **Step 3: Delete `HudView`**

```bash
git rm src/main/java/com/club/ui/devhud/HudView.java
```

- [ ] **Step 4: Compile + full test suite**

Run: `.\gradlew.bat compileJava test`
Expected: BUILD SUCCESSFUL; HudView no longer referenced.

- [ ] **Step 5: Headless screenshot smoke test**

Run (PowerShell): `$env:CLUB_HUD=1; .\gradlew.bat --no-daemon runClient; Remove-Item Env:CLUB_HUD`
Expected: client boots, captures `run/screenshots/*` for preview + editor, stops. Open the shots: elements render, editor shows toolbar + (after the scripted nothing-selected state) no popover; no crash.

- [ ] **Step 6: Manual in-game check**

Run `.\gradlew.bat runClient`, join a world, press **J**. Verify: drag each element (snaps to edges/center with guides), toggle grid-snap (drags quantize to 8px), click an element (popover opens with Enabled/Size/extra), change Size/Range/Layout (element updates live), press Reset (positions restore), Done/ESC closes. Reopen J — positions persisted. Confirm the **legacy** HUD (Right-Shift menu → Misc → HUD Editor, or in-world armor/potion/target) is unchanged.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java/com/club/ui/devhud/
git commit -m "feat(ui): functional V2 HUD editor + preview on HudCanvas; remove HudView [Stage5]"
```

**→ PAUSE for owner review (end of Phase 2: working infrastructure).**

---

## Phase 3 — Real game data for the in-scope elements

Each task swaps the element's sample provider for live data (with sample fallback when no player/world), without changing layout. Verified manually in-world (data can't be unit-tested).

### Task 3.1: Expose legacy data accessors (additive)

**Files:** Modify `src/main/java/com/club/hud/TargetHud.java`, `PotionHud.java`.

- [ ] **Step 1:** In `TargetHud`, change `private static LivingEntity raycastTarget(...)` → `public static LivingEntity raycastTarget(...)` (signature unchanged).
- [ ] **Step 2:** In `PotionHud`, change `private static List<StatusEffectInstance> effects(...)`, `private static String title(...)`, `private static String time(...)` → `public static`.
- [ ] **Step 3:** Compile: `.\gradlew.bat compileJava` → BUILD SUCCESSFUL. Legacy behavior unchanged (only visibility widened).
- [ ] **Step 4:** Commit: `git commit -am "refactor(hud): expose raycastTarget/effects/title/time for V2 reuse [Stage5]"`

### Task 3.2: `EffectsElement` live data

**Files:** Modify `EffectsElement.java`.

- [ ] **Step 1:** Replace `rows(...)` body:
```java
private String[][] rows(MinecraftClient mc, boolean live) {
    if (!live) return new String[][]{{"Speed II", "1:24"}, {"Strength I", "0:42"}};
    var fx = com.club.hud.PotionHud.effects(mc);
    if (fx.isEmpty()) return new String[][]{{"Speed II", "1:24"}, {"Strength I", "0:42"}};
    String[][] out = new String[fx.size()][2];
    for (int i = 0; i < fx.size(); i++) { out[i][0] = com.club.hud.PotionHud.title(fx.get(i)); out[i][1] = com.club.hud.PotionHud.time(fx.get(i)); }
    return out;
}
```
- [ ] **Step 2:** Compile + manual: in-world with active effects, the V2 preview/editor (J) lists real effects, expiring first. `.\gradlew.bat compileJava`.
- [ ] **Step 3:** Commit: `git commit -am "feat(ui): EffectsElement live status-effects [Stage5]"`

### Task 3.3: `TargetElement` live data

**Files:** Modify `TargetElement.java`.

- [ ] **Step 1:** Compute live target in `paint` (before drawing), falling back to sample:
```java
String name = "Steve_42", sub = "18.6 HP"; float frac = 0.62f;
if (live && mc.world != null) {
    var le = com.club.hud.TargetHud.raycastTarget(mc, 1f);
    if (le != null) {
        name = le.getName().getString(); if (name.length() > 18) name = name.substring(0, 17) + "…";
        float hp = le.getHealth(), max = le.getMaxHealth();
        frac = max > 0 ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
        // HP value only — no distance (minimalist design); mirrors legacy TargetHud.trim()
        sub = (Math.abs(hp - Math.round(hp)) < 0.05f ? String.valueOf(Math.round(hp))
                : String.format(java.util.Locale.ROOT, "%.1f", hp)) + " HP";
    }
}
```
(Remove the now-unused instance fields `name/sub/frac`; make them locals in `paint`.)
- [ ] **Step 2:** Compile + manual: look at a mob in-world with J open → real name + HP + HP line; line turns red < 30% (no distance/avatar). `.\gradlew.bat compileJava`.
- [ ] **Step 3:** Commit: `git commit -am "feat(ui): TargetElement live raycast name/dist/HP [Stage5]"`

### Task 3.4: `InfoElement` live data

**Files:** Modify `InfoElement.java`.

- [ ] **Step 1:** Replace `rows(...)`:
```java
private String[][] rows(MinecraftClient mc, boolean live) {
    String fps = (live ? mc.getCurrentFps() : 240) + "";
    String xyz = (live && mc.player != null)
        ? String.format(java.util.Locale.ROOT, "%.0f / %.0f / %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
        : "128 / 72 / -340";
    return new String[][]{{"FPS", fps, "0"}, {"XYZ", xyz, "1"}};
}
```
- [ ] **Step 2:** Compile + manual: J in-world shows real FPS + coordinates. `.\gradlew.bat compileJava`.
- [ ] **Step 3:** Commit: `git commit -am "feat(ui): InfoElement live FPS + XYZ [Stage5]"`

**→ PAUSE for owner review (end of Phase 3: real data).**

---

## Phase 4 — Armor element (GATED on Open Decision #1)

Do **not** start until the owner picks an Armor approach. If **(A) defer** (the plan's assumption), this phase is skipped and Stage 5 ends at Phase 3. If **(B) vector-only** or **(C) hybrid icon**, a follow-up task spec will be written then (it reuses `ArmorHud.frac`/`value` made public, binds `armor*` config, and either renders value+dot only or layers `dc.drawItem`). Left intentionally unspecified to avoid an unconfirmed design decision.

---

## Out of scope this stage (explicit, with reasons)

- **Arraylist** — needs a real module registry / enabled-set (only 3 stateless config-helper modules exist today).
- **Keystrokes / CPS / BPS** — need key-state polling + a click/move ring buffer (net-new tracker, likely an event/mixin).
- **Toast/notifications** — need a notification queue (net-new).
- **In-world V2 rendering + scaffolding removal** — separate later stage: hook the canvas into `HudManager`'s `HudRenderCallback` (or a parallel one), switch off legacy dispatch, then remove `devhud`/`devmenu`/`devgallery`/bootstraps + H/J/G/K — only after owner approval.

These are logged here so nothing is silently dropped.

---

## Self-Review

**Spec coverage (owner's Stage-5 asks):**
- drag & drop → Task 2.1 (grab-offset, tested) + 2.3 (wired). ✓
- snapping → Task 0.1 (`snapAxis`, tested) + 2.1. ✓
- grid → Task 0.1 (`snapToGrid`) + 2.1 (`gridSnap` toggle) + 2.3 (toolbar). ✓
- save positions → Task 2.1 (`saver`/`ClubConfig.save`) + 2.2 (config v5). ✓
- scaling → `HudElement.scaledBox` (1.1, tested) + per-element Size slider (2.3); baked into every `paint`. ✓
- real game data → Phase 3 (Effects/Target/Info live, with sample fallback). ✓
- proper HUD architecture → `HudElement`/`HudCanvas`/elements (1.1, 2.1, 2.2). ✓
- reuse `ClubConfig.Hud` → Effects/Target reuse `potion*`/`target*`; only Info adds fields (necessary). ✓
- coexistence (legacy untouched) → no `HudManager`/`hud/*` render changes; only additive visibility widening in 3.1. ✓

**Placeholder scan:** Phase 4 is intentionally a gated stub (owner decision), not a hidden placeholder; everything in Phases 0–3 has concrete code + commands. The two "confirm signature" notes (Palette `stateBad` accessor; `Label` ctor) are verification steps against in-repo usage, not invented APIs.

**Type consistency:** `HudElement` config-binding method names (`cfgX/cfgY/cfgScale/cfgEnabled`) are used identically by all three elements, the canvas, and the editor popover; `HudCanvas.Snap`/guide names match `HudSnap`; `scaledBox` signature is identical across `HudElement`, its test, and `HudCanvasDragTest.FakeElement.box`.

---

## Execution Handoff

Plan saved to `docs/UI-V2-STAGE5-PLAN.md`. After owner approval, two execution options:

1. **Subagent-Driven (recommended)** — a fresh subagent per task, two-stage review between tasks (`superpowers:subagent-driven-development`).
2. **Inline Execution** — batch execution in this session with checkpoints (`superpowers:executing-plans`).

Pause for owner review at the end of Phase 2 and Phase 3 (do not auto-continue to a new phase).
