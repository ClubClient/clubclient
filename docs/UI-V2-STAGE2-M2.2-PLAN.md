# M2.2 Core Widgets — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Канон:** `docs/UI-V2-STAGE2-M2.2-SPEC.md` (APPROVED, Widget API §3 FROZEN). Foundation-контракты —
> `docs/UI-V2.md` / frozen `docs/UI-V2-STAGE2-SPEC.md`. Не дублировать логику render/input/layout/anim между виджетами.

**Goal:** Реализовать 10 core-виджетов (`Label`, `Divider`, `Panel`, `Card`, `Window`, `ScrollArea`, `Button`,
`Toggle`, `Checkbox`, `Slider`) в `com.club.ui.component.widget` поверх готовой M2.1/infra-prep базы, с headless
логика-тестами и gated dev-галереей для визуальной приёмки.

**Architecture:** Leaf-виджеты расширяют `Component`, составные — `Container` (через внутренние `Column`/`Row`/`Stack`).
Рендер — только через `ctx.renderer()`/`ctx.text()`; значения — только из `Tokens.*`. Drag/press — через готовый
`pressedChild`-capture (`Component.mouseDragged`/`Container`); состояния — через `Tokens.interaction()`.

**Tech Stack:** Java 21, Fabric 1.21.1, JUnit 5, Gradle (`./gradlew.bat`). LWJGL GLFW key-константы для клавиатуры.

## Global Constraints

Каждая задача неявно включает эти правила (verbatim из спеки):

- **Backend-only.** Рендер только через `ctx.renderer()`/`ctx.text()`. Вне `backend/` запрещены
  `RenderSystem`/`DrawContext.fill`/`.drawText(`/`ClubFont`/`RenderHelper`/`BufferBuilder`/`GlUniform`. `ArchitectureRuleTest` зелёный.
- **Tokens — единственный источник.** Никаких литералов цвета/радиуса/размера/отступа/длительности. Px-значения дизайна
  (1px divider, 2px track) — через `border().thickness()` и кратные. State-визуал — только `Tokens.interaction()`.
- **Position-passive** (кроме `Window`): виджет рисует в назначенных `x,y,w,h`; не позиционирует себя/соседей.
- **Alloc-free hot path:** `TextStyle`/`Transition`/callbacks/дети — в полях, не в `render()`/`measure()`.
- **Зона M2.2 — только `com.club.ui.component.widget`** (+ gated галерея). Foundation база (`component/`, `theme/`,
  `layout/`, `motion/`, `backend/`, контракты) виджетами **не меняется**.
- **Headless-тест-правило (критично):** `Ui.text().width(...)` в тестах падает (нет `MinecraftClient`); `lineHeight()`
  — чистая (ok). Поэтому **текст-`width` трогается только в `measure()`** — никогда в `layout()`, input-хендлерах
  или при позиционировании по высоте. Тексто-зависимые `measure()`/`render()` НЕ покрываются headless-тестами
  (проверка — галерея); логика тестируется чистыми helper'ами, bounds через `layout(...)` и **stub-детьми** (фикс-`measure`).
- **Build:** `./gradlew.bat compileJava` / `./gradlew.bat test --tests "<FQN>"`. **Frequent commits** на ветке
  `feat/ui-v2-m2.2-core` (НЕ merge — это делает пользователь).

**Token-аксессоры (verbatim):** `Tokens.surface().surface()/.bg0()/.surfaceHi()/.bg2()`;
`Tokens.accent().accent()/.accentHi()/.gradientA()/.gradientB()/.onAccent()`;
`Tokens.border().subtle()/.defaultColor()/.strong()/.thickness()`; `Tokens.radius().xs()/.sm()/.md()/.lg()/.xl()`;
`Tokens.spacing().xs()/.sm()/.md()/.lg()/.xl()/.xxl()`; `Tokens.type().display()/.title()/.heading()/.body()/.label()/.caption()`
→ `Role.weight()/.size()/.lineHeight()`; `Tokens.elevation().level1()/.level2()` → `Level.surface()/.border()/.shadow()/.glow()`;
`Tokens.glow().active()` → `Preset.size()/.color()`; `Tokens.interaction()` →
`hoverWash()/pressOverlay()/disabledAlpha()/focusRing()/focusRingWidth()`; `Tokens.motion().durations().fast()/.normal()`,
`Tokens.motion().easings().standard()/.decelerate()`; хелперы `Color.withAlpha/lerp/scaleAlpha`.

**Renderer-методы (verbatim):** `rect(x,y,w,h,color)`, `roundedRect(x,y,w,h,radius,color)`,
`border(x,y,w,h,radius,thickness,color)`, `gradient(x,y,w,h,radius,colorA,colorB,Axis)`,
`glow(x,y,w,h,radius,size,color)`, `shadow(x,y,w,h,radius,dx,dy,blur,color)`, `line(x1,y1,x2,y2,thickness,color)`,
`circle(cx,cy,r,color)`, `pushClip/pushRoundedClip/popClip`, `pushOpacity/popOpacity`.
**Text:** `ctx.text().draw(text,x,y,TextStyle)`, `width(text,weight,size)`, `lineHeight(weight,size)`.

**Параллелизация (правило проекта):** один субагент — один независимый виджет (непересекающиеся файлы). Внутри фазы
виджеты независимы. Главный агент делает финальную интеграцию/ревью.

---

## Shared internals (composition mandate, req #3) — DONE in Phase 1

Чтобы не дублировать render/input между виджетами, Phase 1 ввёл два package-private internal-класса в
`component/widget` (НЕ публичный API, НЕ Foundation). **Все виджеты Phase 2/3 ОБЯЗАНЫ их использовать**, а не копировать
последовательности вызовов:

```java
// WidgetPaint — единый источник render-сниппетов (render-only):
static void elevation(UiContext, x,y,w,h,radius, Elevation.Level);  // shadow→fill→border(+glow). Card=level1, Window=level2
static void flatSurface(UiContext, x,y,w,h,radius, int fill);       // плоская поверхность + subtle border. Panel
static void focusRing(UiContext, Component c, float radius);        // ring по bounds c, только если focused
static void hoverWash(UiContext, x,y,w,h,radius, float t);          // overlay hoverWash()*t
static void pressOverlay(UiContext, x,y,w,h,radius);                // overlay pressOverlay()

// Control extends Component (abstract) — commit-модель + клавиатура для Button/Toggle/Checkbox:
//   mouseClicked(btn0)->pressed=true,capture ; mouseReleased->release-inside=activate(), outside=cancel ;
//   keyPressed Space/Enter (enabled)->activate(). Подкласс реализует protected abstract void activate().
```

Правила для виджетов:
- `Button`/`Toggle`/`Checkbox` **extends `Control`** (реализуют `activate()` + `render`); не переопределяют commit-логику.
- focus-ring во всех интерактивных — только `WidgetPaint.focusRing(ctx, this, radius)`; никаких ручных `border(...)` фокус-рамок.
- `Card`/`Window` фон — `WidgetPaint.elevation(...)`; `Panel` — `WidgetPaint.flatSurface(...)`.
- hover/press overlay — `WidgetPaint.hoverWash/pressOverlay`. `Slider`-track (drag-модель, не `Control`) — focus-ring через `WidgetPaint`.

> Tasks ниже (T8–T11) написаны до этого решения и показывают ручной render — **при реализации заменить дублирующийся
> код на вызовы `WidgetPaint`/`Control`**. Контракт (публичный API §3) не меняется.

---

## Phase 0 — Infra commit (G4 onAccent)

### Task 0: Commit G4 `onAccent` (уже реализован, build зелёный)

**Files:**
- Modify (готово в рабочем дереве): `src/main/java/com/club/ui/theme/Accent.java`,
  `src/main/java/com/club/ui/theme/themes/ClubDark.java`, `src/test/java/com/club/ui/theme/TokensTest.java`
- Modify (готово): `docs/UI-V2-STAGE2-SPEC.md` (§3.3/§3.8/§10-поправка-2)

- [ ] **Step 1: Verify green**

Run: `./gradlew.bat test --tests "com.club.ui.theme.TokensTest"`
Expected: `BUILD SUCCESSFUL` (onAccent↔ink0 assertion passes).

- [ ] **Step 2: Commit infra**

```bash
git add src/main/java/com/club/ui/theme/Accent.java src/main/java/com/club/ui/theme/themes/ClubDark.java \
        src/test/java/com/club/ui/theme/TokensTest.java docs/UI-V2-STAGE2-SPEC.md
git commit -m "feat(ui): G4 Accent.onAccent token (foreground on accent fill) [Stage 2/M2.2-prep]"
```

---

## Phase 1 — Callbacks + non-interactive leaves

Независимы между собой (разные файлы); `Label`/`Divider` нужны Phase 2/3 → Phase 1 идёт первой.

### Task 1: Callback types `BoolConsumer`, `FloatConsumer`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/BoolConsumer.java`
- Create: `src/main/java/com/club/ui/component/widget/FloatConsumer.java`
- Test: `src/test/java/com/club/ui/component/widget/CallbackTypesTest.java`

**Interfaces:**
- Produces: `BoolConsumer { void accept(boolean v); }`, `FloatConsumer { void accept(float v); }` — используются
  `Toggle`/`Checkbox` (`onChange(BoolConsumer)`) и `Slider` (`onChange(FloatConsumer)`).

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CallbackTypesTest {
    @Test void boolConsumerReceivesValue() {
        boolean[] got = { false };
        BoolConsumer cb = v -> got[0] = v;
        cb.accept(true);
        assertTrue(got[0]);
    }
    @Test void floatConsumerReceivesValue() {
        float[] got = { 0f };
        FloatConsumer cb = v -> got[0] = v;
        cb.accept(2.5f);
        assertEquals(2.5f, got[0], 1e-6);
    }
}
```

- [ ] **Step 2: Run → FAIL** (`./gradlew.bat test --tests "com.club.ui.component.widget.CallbackTypesTest"`) — `BoolConsumer not found`.

- [ ] **Step 3: Implement**

```java
// BoolConsumer.java
package com.club.ui.component.widget;
@FunctionalInterface public interface BoolConsumer { void accept(boolean value); }
```
```java
// FloatConsumer.java
package com.club.ui.component.widget;
@FunctionalInterface public interface FloatConsumer { void accept(float value); }
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): widget callback types BoolConsumer/FloatConsumer [M2.2]"`

### Task 2: `Label`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Label.java`
- Test: `src/test/java/com/club/ui/component/widget/LabelTest.java`

**Interfaces:**
- Consumes: `Component`, `Typography.Role` (`Tokens.type().body()`), `Align`, `TextEffect`, `TextStyle`, `Ui.text()`.
- Produces: `new Label(String)`, `new Label(String, Typography.Role)`, fluent `text/role/align/color/effect`;
  `measure()` (text-width, NOT unit-tested), `render()`. Используется Button/Card/Window/Slider.

> **measure/render — не headless-тестируемы** (text-`width`). Тест покрывает только builder/state (без `measure`).

- [ ] **Step 1: Write the failing test** (builder/state only)

```java
package com.club.ui.component.widget;
import com.club.ui.text.Align;
import com.club.ui.theme.Tokens;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabelTest {
    @Test void buildersAreFluentAndStoreState() {
        Label l = new Label("Hi").align(Align.CENTER).color(0xFF112233);
        assertSame(l, l.text("Bye"));           // fluent returns this
        // exposed for gallery/assertions:
        assertEquals("Bye", l.textValue());
        assertEquals(Align.CENTER, l.alignValue());
        assertEquals(0xFF112233, l.colorValue());
        assertEquals(Tokens.type().body().size(), l.role().size(), 1e-6); // default role = body
    }
}
```

- [ ] **Step 2: Run → FAIL** (`Label not found`).

- [ ] **Step 3: Implement** (logic full; render via spec §3.1 recipe)

```java
package com.club.ui.component.widget;
import com.club.ui.Ui; import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.text.*;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

public final class Label extends Component {
    private String text;
    private Typography.Role role;
    private Align align = Align.LEFT;
    private int color; private boolean colorSet;
    private TextEffect effect = TextEffect.NONE;
    private TextStyle style; private boolean styleDirty = true;   // lazy, alloc-free in render

    public Label(String text) { this(text, Tokens.type().body()); }
    public Label(String text, Typography.Role role) { this.text = text; this.role = role; }

    public Label text(String s)   { this.text = s; styleDirty = true; return this; }
    public Label role(Typography.Role r) { this.role = r; styleDirty = true; return this; }
    public Label align(Align a)   { this.align = a; styleDirty = true; return this; }
    public Label color(int c)     { this.color = c; this.colorSet = true; styleDirty = true; return this; }
    public Label effect(TextEffect e) { this.effect = e; styleDirty = true; return this; }

    // exposed for tests/gallery (no text-metrics)
    public String textValue() { return text; }
    public Align alignValue() { return align; }
    public int colorValue()   { return colorSet ? color : Tokens.palette().textHi(); }
    public Typography.Role role() { return role; }

    @Override public Size measure(float availW, float availH) {   // text-width: NOT headless-tested
        return new Size(Ui.text().width(text, role.weight(), role.size()),
                        Ui.text().lineHeight(role.weight(), role.size()));
    }
    @Override public void render(UiContext ctx) {
        if (styleDirty) { style = TextStyle.of(role.weight(), role.size(), colorValue()).align(align).effect(effect); styleDirty = false; }
        // align: anchor x at left/center/right of assigned bounds (spec §3.1)
        float tx = switch (align) { case LEFT -> x; case CENTER -> x + w / 2f; case RIGHT -> x + w; };
        ctx.text().draw(text, tx, y, style);
    }
}
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Label widget [M2.2]"`

### Task 3: `Divider`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Divider.java`
- Test: `src/test/java/com/club/ui/component/widget/DividerTest.java`

**Interfaces:**
- Consumes: `Component`, `Axis`, `Tokens.border()`.
- Produces: `new Divider()`, `new Divider(Axis)`, `.color(int)`; `measure()` (token-only → headless-ok), `render()`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import com.club.ui.Axis;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DividerTest {
    @Test void horizontalMeasuresThicknessOnCrossAxis() {
        Size s = new Divider().measure(100, 100);          // default HORIZONTAL
        assertEquals(0f, s.w(), 1e-6);
        assertEquals(Tokens.border().thickness(), s.h(), 1e-6);
    }
    @Test void verticalMeasuresThicknessOnWidth() {
        Size s = new Divider(Axis.VERTICAL).measure(100, 100);
        assertEquals(Tokens.border().thickness(), s.w(), 1e-6);
        assertEquals(0f, s.h(), 1e-6);
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement**

```java
package com.club.ui.component.widget;
import com.club.ui.Axis; import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;

public final class Divider extends Component {
    private final Axis axis;
    private int color; private boolean colorSet;
    public Divider() { this(Axis.HORIZONTAL); }
    public Divider(Axis axis) { this.axis = axis; }
    public Divider color(int c) { this.color = c; this.colorSet = true; return this; }
    private int color() { return colorSet ? color : Tokens.border().subtle(); }

    @Override public Size measure(float availW, float availH) {
        float t = Tokens.border().thickness();
        return axis == Axis.HORIZONTAL ? new Size(0, t) : new Size(t, 0);
    }
    @Override public void render(UiContext ctx) { ctx.renderer().rect(x, y, w, h, color()); }
}
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Divider widget [M2.2]"`

---

## Phase 2 — Containers

Независимы между собой; зависят от `Label`/`Divider` (Phase 1) для композиции. `Panel`/`Card`/`ScrollArea`/`Window`.
**Тест-правило:** контейнеры тестируются со **stub-детьми** (фикс-`measure`, без текста), напр.:
```java
static final class Box extends com.club.ui.component.Component {
    final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
    @Override public com.club.ui.layout.Size measure(float a, float b){ return new com.club.ui.layout.Size(pw, ph); }
    @Override public void render(com.club.ui.UiContext c){}
}
```

### Task 4: `Panel`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Panel.java`
- Test: `src/test/java/com/club/ui/component/widget/PanelTest.java`

**Interfaces:**
- Consumes: `Container`, `Insets`, `Size`, `Tokens.surface()/.radius()/.border()`.
- Produces: `new Panel()`, `new Panel(Component)`, `.child(Component)`, `.padding(Insets)`; `measure`/`layout`/`render`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import com.club.ui.layout.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }
    @Test void measureAddsPadding() {
        Panel p = new Panel(new Box(40, 20)).padding(Insets.all(8));
        Size s = p.measure(200, 200);
        assertEquals(40 + 16, s.w(), 1e-6);
        assertEquals(20 + 16, s.h(), 1e-6);
    }
    @Test void layoutPlacesChildInInnerRect() {
        Box b = new Box(40, 20);
        Panel p = new Panel(b).padding(Insets.all(8));
        p.layout(10, 10, 100, 60);
        assertEquals(18f, b.xLeft(), 1e-6);   // 10 + left padding
        assertEquals(18f, b.yTop(),  1e-6);
        assertEquals(84f, b.width(), 1e-6);    // 100 - 16
        assertEquals(44f, b.height(),1e-6);
    }
    @Test void emptyPanelMeasuresPaddingOnly() {
        assertEquals(16f, new Panel().padding(Insets.all(8)).measure(50,50).w(), 1e-6);
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (render via spec §3.3)

```java
package com.club.ui.component.widget;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;

public final class Panel extends Container {
    private Component child;
    private Insets padding = Insets.ZERO;
    public Panel() {}
    public Panel(Component c) { child(c); }
    public Panel child(Component c) { this.child = c; children.clear(); if (c != null) addChild(c); return this; }
    public Panel padding(Insets p) { this.padding = p; return this; }

    @Override public Size measure(float availW, float availH) {
        if (child == null) return new Size(padding.horizontal(), padding.vertical());
        Size c = child.measure(availW - padding.horizontal(), availH - padding.vertical());
        return new Size(c.w() + padding.horizontal(), c.h() + padding.vertical());
    }
    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        if (child != null) child.layout(x + padding.left(), y + padding.top(),
                                        w - padding.horizontal(), h - padding.vertical());
    }
    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().md();
        ctx.renderer().roundedRect(x, y, w, h, r, Tokens.surface().surface());
        ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), Tokens.border().subtle());
        if (child != null) { ctx.renderer().pushRoundedClip(x, y, w, h, r); child.render(ctx); ctx.renderer().popClip(); }
    }
}
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Panel widget [M2.2]"`

### Task 5: `Card`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Card.java`
- Test: `src/test/java/com/club/ui/component/widget/CardTest.java`

**Interfaces:**
- Consumes: `Container`, `Column`, `Divider`, `Insets`, `Tokens.elevation().level1()/.radius()/.spacing()`.
- Produces: `new Card()`, `new Card(Component)`, `.content/.header/.footer(Component)`, `.padding(Insets)`; `measure`/`layout`/`render`.

- [ ] **Step 1: Write the failing test** (structure via stubs; build internal Column, assert stacking order/positions)

```java
package com.club.ui.component.widget;
import com.club.ui.layout.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }
    @Test void stacksHeaderContentFooterTopToBottom() {
        Box header = new Box(50, 10), content = new Box(50, 30), footer = new Box(50, 8);
        Card card = new Card(content).header(header).footer(footer).padding(Insets.ZERO);
        card.layout(0, 0, 50, 48);
        assertTrue(header.yTop() < content.yTop());
        assertTrue(content.yTop() < footer.yTop());
    }
    @Test void contentOnlyCardLaysOut() {
        Box content = new Box(50, 30);
        Card card = new Card(content).padding(Insets.ZERO);
        card.layout(0, 0, 50, 30);
        assertEquals(0f, content.yTop(), 1e-6);
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (internal Column; render via spec §3.4)

```java
package com.club.ui.component.widget;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.*;
import com.club.ui.theme.Elevation;
import com.club.ui.theme.Tokens;

public final class Card extends Container {
    private Component header, content, footer;
    private Insets padding = Insets.symmetric(Tokens.spacing().md(), Tokens.spacing().md());
    private Column column; private boolean dirty = true;
    public Card() {}
    public Card(Component c) { this.content = c; }
    public Card content(Component c) { this.content = c; dirty = true; return this; }
    public Card header(Component c)  { this.header = c;  dirty = true; return this; }
    public Card footer(Component c)  { this.footer = c;  dirty = true; return this; }
    public Card padding(Insets p)    { this.padding = p; dirty = true; return this; }

    private Column column() {
        if (dirty) {
            column = new Column().padding(padding).gap(Tokens.spacing().sm());
            if (header != null) { column.add(header); column.add(new Divider()); }
            if (content != null) column.add(content, Sizing.fill());
            if (footer != null) { column.add(new Divider()); column.add(footer); }
            children.clear(); children.add(column);
            dirty = false;
        }
        return column;
    }
    @Override public Size measure(float availW, float availH) { return column().measure(availW, availH); }
    @Override public void layout(float x, float y, float w, float h) { super.layout(x, y, w, h); column().layout(x, y, w, h); }
    @Override public void render(UiContext ctx) {
        Elevation.Level lv = Tokens.elevation().level1();
        float r = Tokens.radius().lg();
        var sh = lv.shadow();
        ctx.renderer().shadow(x, y, w, h, r, sh.dx(), sh.dy(), sh.blur(), sh.color());
        ctx.renderer().roundedRect(x, y, w, h, r, lv.surface());
        ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), lv.border());
        ctx.renderer().pushRoundedClip(x, y, w, h, r); column().render(ctx); ctx.renderer().popClip();
    }
}
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Card widget [M2.2]"`

### Task 6: `ScrollArea`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/ScrollArea.java`
- Test: `src/test/java/com/club/ui/component/widget/ScrollAreaTest.java`

**Interfaces:**
- Consumes: `Container`, `Size`, `Tokens.border()/.accent()/.spacing()`, capture (`mouseDragged`).
- Produces: `new ScrollArea(Component)`; vertical wheel-scroll + thumb-drag; `offset` clamp; R13 (база гейтит disabled).
  Pure helper `static float clampOffset(float off, float contentH, float viewportH)` — unit-tested.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollAreaTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }
    @Test void clampOffsetWithinBounds() {
        assertEquals(0f,  ScrollArea.clampOffset(-5, 300, 100), 1e-6);   // no negative
        assertEquals(200f,ScrollArea.clampOffset(999, 300, 100), 1e-6);  // max = content-viewport
        assertEquals(0f,  ScrollArea.clampOffset(50, 80, 100), 1e-6);    // content<viewport → no scroll
    }
    @Test void wheelScrollMovesOffsetAndConsumes() {
        ScrollArea sa = new ScrollArea(new Box(100, 300));
        sa.layout(0, 0, 100, 100);                       // viewport 100, content 300 → scrollable
        boolean consumed = sa.mouseScrolled(10, 10, -1); // wheel down
        assertTrue(consumed);
        assertTrue(sa.offset() > 0f);
    }
    @Test void noScrollWhenContentFits() {
        ScrollArea sa = new ScrollArea(new Box(100, 50));
        sa.layout(0, 0, 100, 100);
        assertFalse(sa.mouseScrolled(10, 10, -1));       // nothing to scroll → not consumed
        assertEquals(0f, sa.offset(), 1e-6);
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (wheel + thumb-drag; render via spec §3.6)

```java
package com.club.ui.component.widget;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;

public final class ScrollArea extends Container {
    private final Component content;
    private float offset, contentH, viewportH, scrollStep;
    private boolean draggingThumb; private float thumbGrabY;
    public ScrollArea(Component content) { this.content = content; addChild(content); }

    public float offset() { return offset; }
    static float clampOffset(float off, float contentH, float viewportH) {
        float max = Math.max(0f, contentH - viewportH);
        return Math.max(0f, Math.min(off, max));
    }
    @Override public Size measure(float availW, float availH) { return content.measure(availW, availH); }
    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        viewportH = h; scrollStep = Tokens.spacing().xl();
        contentH = content.measure(w, h).h();
        offset = clampOffset(offset, contentH, viewportH);
        content.layout(x, y - offset, w, contentH);     // virtualization seam: all children laid out (frozen §11)
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        // R13: база (Container) уже не зовёт нас при !enabled; здесь — только активная логика
        float before = offset;
        offset = clampOffset(offset - (float) amount * scrollStep, contentH, viewportH);
        if (offset != before) { content.layout(x, y - offset, w, contentH); return true; }
        return false;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (overflowing() && inThumb(mx, my)) { draggingThumb = true; thumbGrabY = (float) my; return true; }
        return super.mouseClicked(mx, my, button);      // route to content child (capture)
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingThumb) {
            float trackRange = Math.max(1f, viewportH - thumbHeight());
            offset = clampOffset(offset + (float) dy * (contentH - viewportH) / trackRange, contentH, viewportH);
            content.layout(x, y - offset, w, contentH); return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (draggingThumb) { draggingThumb = false; return true; }
        return super.mouseReleased(mx, my, button);
    }
    private boolean overflowing() { return contentH > viewportH; }
    private float thumbHeight() { return Math.max(Tokens.spacing().lg(), viewportH * viewportH / Math.max(1f, contentH)); }
    private float thumbY()      { return y + (viewportH - thumbHeight()) * (offset / Math.max(1f, contentH - viewportH)); }
    private float barW()        { return Tokens.spacing().xs(); }
    private boolean inThumb(double mx, double my) {
        float bx = x + w - barW();
        return mx >= bx && mx <= x + w && my >= thumbY() && my <= thumbY() + thumbHeight();
    }
    @Override public void render(UiContext ctx) {
        ctx.renderer().pushClip(x, y, w, viewportH);
        content.render(ctx);
        ctx.renderer().popClip();
        if (overflowing()) {
            float bx = x + w - barW(), r = barW() / 2f;
            ctx.renderer().roundedRect(bx, y, barW(), viewportH, r, Tokens.border().subtle());
            ctx.renderer().roundedRect(bx, thumbY(), barW(), thumbHeight(), r, Tokens.accent().accent());
        }
    }
}
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): ScrollArea widget (wheel + thumb-drag) [M2.2]"`

### Task 7: `Window`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Window.java`
- Test: `src/test/java/com/club/ui/component/widget/WindowTest.java`

**Interfaces:**
- Consumes: `Container`, `Column`, `Sizing`, capture (`mouseDragged`), `Tokens.elevation().level2()/.radius()/.surface()/.type()`.
- Produces: `new Window(String)`, `.content(Component)`, `.position(float,float)`, `.size(float,float)`. Внутренний
  private `TitleBar` (drag-handle, height = `lineHeight`-token, рисует title в render — **width не мерится**, headless-ok).
  `moveBy(dx,dy)` сдвигает x,y и релэйаутит.

> **Drag — by-reference** (§3.5/§F): `TitleBar.mouseClicked → true` (capture), `TitleBar.mouseDragged → window.moveBy`.
> `Window` НЕ переопределяет диспатч. Никаких `dragging`-флага/`titleBarContains`.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }
    @Test void titleBarDragMovesWindowByReference() {
        Window win = new Window("Settings").content(new Box(180, 120)).size(200, 160).position(40, 40);
        win.layout(40, 40, 200, 160);
        // press in title-bar (top strip), then drag — capture routes to the title-bar handle
        assertTrue(win.mouseClicked(60, 46, 0));         // consumes → captured
        win.mouseDragged(85, 71, 0, 25, 25);
        assertEquals(65f, win.xLeft(), 1e-6);            // moved by (25,25)
        assertEquals(65f, win.yTop(),  1e-6);
        win.mouseReleased(85, 71, 0);
    }
    @Test void pressOnContentDoesNotMoveWindow() {
        Box content = new Box(180, 120);
        Window win = new Window("S").content(content).size(200, 160).position(0, 0);
        win.layout(0, 0, 200, 160);
        win.mouseClicked(100, 120, 0);                   // inside content area (below title bar)
        win.mouseDragged(120, 140, 0, 20, 20);
        assertEquals(0f, win.xLeft(), 1e-6);             // window not moved (content has no drag)
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (TitleBar handle by-reference; render via spec §3.5)

```java
package com.club.ui.component.widget;
import com.club.ui.Ui; import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.*;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

public final class Window extends Container {
    private final String title;
    private Component content;
    private float winW, winH;
    private final Column column = new Column();
    private final TitleBar titleBar = new TitleBar();
    private boolean built;

    public Window(String title) { this.title = title; }
    public Window content(Component c) { this.content = c; built = false; return this; }
    public Window position(float x, float y) { layout(x, y, winW, winH); return this; }
    public Window size(float w, float h) { this.winW = w; this.winH = h; built = false; return this; }
    void moveBy(float dx, float dy) { layout(x + dx, y + dy, w, h); }

    private void build() {
        column.padding(Insets.ZERO).gap(0f);
        children.clear(); column.children().clear();
        column.add(titleBar);
        if (content != null) column.add(content, Sizing.fill());
        children.add(column);
        built = true;
    }
    @Override public Size measure(float availW, float availH) { return new Size(winW, winH); }
    @Override public void layout(float x, float y, float w, float h) {
        if (!built) build();
        super.layout(x, y, winW > 0 ? winW : w, winH > 0 ? winH : h);
        column.layout(this.x, this.y, this.w, this.h);
    }
    @Override public void render(UiContext ctx) {
        var lv = Tokens.elevation().level2(); float r = Tokens.radius().lg(); var sh = lv.shadow();
        ctx.renderer().shadow(x, y, w, h, r, sh.dx(), sh.dy(), sh.blur(), sh.color());
        ctx.renderer().roundedRect(x, y, w, h, r, lv.surface());
        ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), lv.border());
        ctx.renderer().pushRoundedClip(x, y, w, h, r); column.render(ctx); ctx.renderer().popClip();
    }

    /** Draggable title-bar handle: consumes press (capture), drags the owning Window by reference. */
    private final class TitleBar extends Component {
        @Override public Size measure(float availW, float availH) {   // lineHeight is headless-safe (no text-width)
            Typography.Role role = Tokens.type().title();
            return new Size(0, Ui.text().lineHeight(role.weight(), role.size()) + Tokens.spacing().md());
        }
        @Override public boolean mouseClicked(double mx, double my, int button) { return true; } // capture
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            Window.this.moveBy((float) dx, (float) dy); return true;
        }
        @Override public boolean mouseReleased(double mx, double my, int button) { return true; }
        @Override public void render(UiContext ctx) {
            ctx.renderer().rect(x, y, w, h, Tokens.surface().surfaceHi());
            Typography.Role role = Tokens.type().title();
            TextStyle st = TextStyle.of(role.weight(), role.size(), Tokens.palette().textHi());
            ctx.text().draw(title, x + Tokens.spacing().md(), y + Tokens.spacing().sm(), st);
        }
    }
}
```

> **Note:** `super.mouseClicked` (Container) iterates children top-z-first → press in title-bar strip hits `TitleBar`
> (returns true → captured); press lower hits `content`. `Window` itself never overrides dispatch — capture routes
> `mouseDragged`/`mouseReleased` to whichever child consumed (TitleBar → `moveBy`; content → its own handler).

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Window widget (by-reference title-bar drag) [M2.2]"`

---

## Phase 3 — Interactive controls

Независимы между собой; зависят от callbacks (Task 1) и `Label` (Task 2). `Button`/`Toggle`/`Checkbox`/`Slider`.
Общая commit-модель: press внутри → capture; release **внутри** → действие; release вне → cancel. Клавиатура — §2.8.
GLFW: `import static org.lwjgl.glfw.GLFW.*;` (`GLFW_KEY_SPACE`, `GLFW_KEY_ENTER`, `GLFW_KEY_LEFT`, `GLFW_KEY_RIGHT`).

### Task 8: `Button`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Button.java`
- Test: `src/test/java/com/club/ui/component/widget/ButtonTest.java`

**Interfaces:**
- Consumes: `Component`, `Label`, `Runnable`, `Transition`, `Tokens.accent()/.interaction()/.radius()/.spacing()`, GLFW keys.
- Produces: `new Button(String)`, `enum Variant{PRIMARY,GHOST}`, `.variant(Variant)`, `.onClick(Runnable)`.
  `layout()` sets bounds only (no text-measure → headless-ok); `keyPressed` activates on SPACE/ENTER.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class ButtonTest {
    @Test void releaseInsideFiresOnClick() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.layout(0, 0, 80, 24);
        assertTrue(b.mouseClicked(10, 10, 0));   // press inside → capture
        b.mouseReleased(10, 10, 0);              // release inside → fire
        assertEquals(1, n[0]);
    }
    @Test void releaseOutsideCancels() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.layout(0, 0, 80, 24);
        b.mouseClicked(10, 10, 0);
        b.mouseReleased(200, 200, 0);            // release outside → cancel
        assertEquals(0, n[0]);
    }
    @Test void disabledIgnoresClick() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.enabled = false; b.layout(0, 0, 80, 24);
        assertFalse(b.mouseClicked(10, 10, 0));
        assertEquals(0, n[0]);
    }
    @Test void keyboardEnterActivates() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.layout(0, 0, 80, 24);
        assertTrue(b.keyPressed(GLFW_KEY_ENTER, 0, 0));
        assertEquals(1, n[0]);
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (commit-model + keyboard; render via spec §3.7)

```java
package com.club.ui.component.widget;
import com.club.ui.Color; import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.text.Align;
import com.club.ui.theme.Tokens;
import static org.lwjgl.glfw.GLFW.*;

public final class Button extends Component {
    public enum Variant { PRIMARY, GHOST }
    private final Label label;
    private Variant variant = Variant.PRIMARY;
    private Runnable onClick;
    private final Transition hover = new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());

    public Button(String text) { this.label = new Label(text).align(Align.CENTER); }
    public Button variant(Variant v) { this.variant = v; return this; }
    public Button onClick(Runnable r) { this.onClick = r; return this; }

    @Override public Size measure(float availW, float availH) {       // text-width: not headless-tested
        Size t = label.measure(availW, availH);
        return new Size(t.w() + Tokens.spacing().md() * 2, t.h() + Tokens.spacing().sm() * 2);
    }
    @Override public void layout(float x, float y, float w, float h) { super.layout(x, y, w, h); } // bounds only

    @Override public boolean mouseClicked(double mx, double my, int b) { if (b == 0) { pressed = true; return true; } return false; }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        boolean fire = pressed && contains(mx, my);
        pressed = false;
        if (fire) activate();
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (enabled && (key == GLFW_KEY_SPACE || key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)) { activate(); return true; }
        return false;
    }
    private void activate() { if (onClick != null) onClick.run(); }

    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().sm(); float now = ctx.time();
        hover.target(hovered ? 1f : 0f, now); float hv = hover.value(now);
        if (variant == Variant.PRIMARY) {
            int fill = Color.lerp(Tokens.accent().accent(), Tokens.accent().accentHi(), hv);
            ctx.renderer().roundedRect(x, y, w, h, r, fill);
            if (pressed) ctx.renderer().roundedRect(x, y, w, h, r, Tokens.interaction().pressOverlay());
            label.color(Tokens.accent().onAccent());
        } else { // GHOST
            ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), Tokens.border().defaultColor());
            if (hv > 0f) ctx.renderer().roundedRect(x, y, w, h, r, Color.scaleAlpha(Tokens.interaction().hoverWash(), hv));
            label.color(Color.lerp(Tokens.palette().textHi(), Tokens.accent().accent(), hv));
        }
        label.layout(x, y + (h - label.measure(w, h).h()) / 2f, w, h);   // text-measure only at render (ctx ready)
        label.render(ctx);
        if (focused) ctx.renderer().border(x, y, w, h, r, Tokens.interaction().focusRingWidth(), Tokens.interaction().focusRing());
        if (!enabled) {} // disabled visual handled by parent pushOpacity(interaction().disabledAlpha()) — see gallery
    }
}
```

> **disabled:** ввод уже не доходит (база гейтит `!enabled`); визуально затемнение даёт обёртка через
> `pushOpacity(interaction().disabledAlpha())` (галерея/контейнер) — виджет не дублирует.

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Button widget (PRIMARY/GHOST, capture commit, keyboard) [M2.2]"`

### Task 9: `Toggle`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Toggle.java`
- Test: `src/test/java/com/club/ui/component/widget/ToggleTest.java`

**Interfaces:**
- Consumes: `Component`, `BoolConsumer`, `Transition`, `Tokens.accent()/.glow()/.surface()/.type()/.interaction()`, GLFW.
- Produces: `new Toggle(boolean)`, `.onChange(BoolConsumer)`, `boolean value()`. No text → fully headless-testable.

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class ToggleTest {
    @Test void releaseInsideFlipsAndNotifies() {
        Boolean[] last = { null };
        Toggle t = new Toggle(false).onChange(v -> last[0] = v);
        t.layout(0, 0, 36, 20);
        t.mouseClicked(5, 5, 0);
        t.mouseReleased(5, 5, 0);
        assertTrue(t.value());
        assertEquals(Boolean.TRUE, last[0]);
    }
    @Test void releaseOutsideDoesNotFlip() {
        Toggle t = new Toggle(false);
        t.layout(0, 0, 36, 20);
        t.mouseClicked(5, 5, 0);
        t.mouseReleased(100, 100, 0);
        assertFalse(t.value());
    }
    @Test void spaceTogglesWhenFocused() {
        Toggle t = new Toggle(false);
        t.layout(0, 0, 36, 20);
        assertTrue(t.keyPressed(GLFW_KEY_SPACE, 0, 0));
        assertTrue(t.value());
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (render via spec §3.8 — ON: accent gradient + active glow; knob `Transition`)

```java
package com.club.ui.component.widget;
import com.club.ui.Axis; import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.theme.Tokens;
import static org.lwjgl.glfw.GLFW.*;

public final class Toggle extends Component {
    private boolean value;
    private BoolConsumer onChange;
    private final Transition knob = new Transition(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
    public Toggle(boolean value) { this.value = value; knob.target(value ? 1f : 0f, 0f); }
    public Toggle onChange(BoolConsumer cb) { this.onChange = cb; return this; }
    public boolean value() { return value; }

    @Override public Size measure(float a, float b) {
        float hgt = Tokens.type().body().lineHeight();
        return new Size(hgt + Tokens.spacing().lg(), hgt);          // width = height + knob travel (token)
    }
    @Override public boolean mouseClicked(double mx, double my, int b) { if (b == 0) { pressed = true; return true; } return false; }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        boolean fire = pressed && contains(mx, my); pressed = false;
        if (fire) flip();
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (enabled && (key == GLFW_KEY_SPACE || key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)) { flip(); return true; }
        return false;
    }
    private void flip() { value = !value; if (onChange != null) onChange.accept(value); }

    @Override public void render(UiContext ctx) {
        float now = ctx.time(); knob.target(value ? 1f : 0f, now); float k = knob.value(now);
        float r = h / 2f;
        if (value) {
            ctx.renderer().gradient(x, y, w, h, r, Tokens.accent().gradientA(), Tokens.accent().gradientB(), Axis.HORIZONTAL);
            var g = Tokens.glow().active(); ctx.renderer().glow(x, y, w, h, r, g.size(), g.color());
        } else {
            ctx.renderer().roundedRect(x, y, w, h, r, Tokens.surface().surfaceHi());
            ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), Tokens.border().defaultColor());
        }
        float kr = r - Tokens.border().thickness() * 2, travel = w - 2 * r;
        ctx.renderer().circle(x + r + travel * k, y + r, kr, Tokens.palette().white());
        if (focused) ctx.renderer().border(x, y, w, h, r, Tokens.interaction().focusRingWidth(), Tokens.interaction().focusRing());
    }
}
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Toggle widget (gradient ON + glow, animated knob) [M2.2]"`

### Task 10: `Checkbox`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Checkbox.java`
- Test: `src/test/java/com/club/ui/component/widget/CheckboxTest.java`

**Interfaces:**
- Consumes: `Component`, `BoolConsumer`, `Transition`, `Tokens.accent()/.surface()/.border()/.radius()/.type()`, GLFW.
- Produces: `new Checkbox(boolean)`, `.onChange(BoolConsumer)`, `boolean value()`. No text → headless-testable.

- [ ] **Step 1: Write the failing test** (mirror Toggle: release-inside flips, outside cancels, SPACE toggles)

```java
package com.club.ui.component.widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class CheckboxTest {
    @Test void releaseInsideTogglesAndNotifies() {
        Boolean[] last = { null };
        Checkbox c = new Checkbox(false).onChange(v -> last[0] = v);
        c.layout(0, 0, 18, 18);
        c.mouseClicked(4, 4, 0); c.mouseReleased(4, 4, 0);
        assertTrue(c.value()); assertEquals(Boolean.TRUE, last[0]);
    }
    @Test void releaseOutsideCancels() {
        Checkbox c = new Checkbox(false);
        c.layout(0, 0, 18, 18);
        c.mouseClicked(4, 4, 0); c.mouseReleased(99, 99, 0);
        assertFalse(c.value());
    }
    @Test void spaceToggles() {
        Checkbox c = new Checkbox(true);
        c.layout(0, 0, 18, 18);
        assertTrue(c.keyPressed(GLFW_KEY_SPACE, 0, 0));
        assertFalse(c.value());
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (render via spec §3.9 — OFF box+border; ON accent + checkmark via 2× `line`; `Transition` check-in)

```java
package com.club.ui.component.widget;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.theme.Tokens;
import static org.lwjgl.glfw.GLFW.*;

public final class Checkbox extends Component {
    private boolean value;
    private BoolConsumer onChange;
    private final Transition check = new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    public Checkbox(boolean value) { this.value = value; check.target(value ? 1f : 0f, 0f); }
    public Checkbox onChange(BoolConsumer cb) { this.onChange = cb; return this; }
    public boolean value() { return value; }

    @Override public Size measure(float a, float b) { float s = Tokens.type().body().lineHeight(); return new Size(s, s); }
    @Override public boolean mouseClicked(double mx, double my, int b) { if (b == 0) { pressed = true; return true; } return false; }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        boolean fire = pressed && contains(mx, my); pressed = false; if (fire) flip(); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (enabled && (key == GLFW_KEY_SPACE || key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)) { flip(); return true; }
        return false;
    }
    private void flip() { value = !value; if (onChange != null) onChange.accept(value); }

    @Override public void render(UiContext ctx) {
        float now = ctx.time(); check.target(value ? 1f : 0f, now); float c = check.value(now);
        float r = Tokens.radius().xs();
        if (c < 1f) { ctx.renderer().roundedRect(x, y, w, h, r, Tokens.surface().surfaceHi());
                      ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), Tokens.border().defaultColor()); }
        if (c > 0f) {
            ctx.renderer().roundedRect(x, y, w, h, r, Tokens.accent().accent());
            float t = Tokens.border().thickness() * 1.5f, cx = x + w * 0.42f, lo = y + h * 0.62f;
            ctx.renderer().line(x + w * 0.26f, y + h * 0.5f, cx, lo, t, Tokens.accent().onAccent());
            ctx.renderer().line(cx, lo, x + w * 0.74f, y + h * 0.32f, t, Tokens.accent().onAccent());
        }
        if (focused) ctx.renderer().border(x, y, w, h, r, Tokens.interaction().focusRingWidth(), Tokens.interaction().focusRing());
    }
}
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Checkbox widget [M2.2]"`

### Task 11: `Slider`

**Files:**
- Create: `src/main/java/com/club/ui/component/widget/Slider.java`
- Test: `src/test/java/com/club/ui/component/widget/SliderTest.java`

**Interfaces:**
- Consumes: `Container`, `Row`, `Sizing`, `Label`, `FloatConsumer`, capture, `Tokens.accent()/.surface()/.border()/.type()/.spacing()`, GLFW.
- Produces: `new Slider(float value,float min,float max,float step)`, `.onChange(FloatConsumer)`, `.showValue(boolean)`,
  `float value()`. Pure helpers (unit-tested): `static float quantize(float raw,float min,float max,float step)`,
  `static String format(float v,float step)`. Логика-тесты используют `showValue(false)` (без text в `layout`).

- [ ] **Step 1: Write the failing test**

```java
package com.club.ui.component.widget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class SliderTest {
    @Test void quantizeClampsAndSnaps() {
        assertEquals(0f,  Slider.quantize(-3, 0, 10, 1), 1e-6);
        assertEquals(10f, Slider.quantize(99, 0, 10, 1), 1e-6);
        assertEquals(4f,  Slider.quantize(4.2f, 0, 10, 1), 1e-6);   // snap to step 1
        assertEquals(4.2f,Slider.quantize(4.2f, 0, 10, 0), 1e-4);   // step 0 = continuous
    }
    @Test void formatIntegerVsFractional() {
        assertEquals("4",   Slider.format(4f, 1f));
        assertEquals("4.2", Slider.format(4.2f, 0.1f));
    }
    @Test void dragMapsMouseToValue() {
        float[] last = { -1 };
        Slider s = new Slider(0, 0, 100, 0).showValue(false).onChange(v -> last[0] = v);
        s.layout(0, 0, 100, 16);                 // track spans full width (no value label)
        s.mouseClicked(0, 8, 0);                  // grab at left → ~min
        s.mouseDragged(50, 8, 0, 50, 0);          // drag to mid → ~50
        assertEquals(50f, s.value(), 1.5f);
        assertEquals(s.value(), last[0], 1e-6);
    }
    @Test void arrowKeysStep() {
        Slider s = new Slider(5, 0, 10, 1).showValue(false);
        s.layout(0, 0, 100, 16);
        assertTrue(s.keyPressed(GLFW_KEY_RIGHT, 0, 0));
        assertEquals(6f, s.value(), 1e-6);
        s.keyPressed(GLFW_KEY_LEFT, 0, 0);
        assertEquals(5f, s.value(), 1e-6);
    }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement** (Row[track, valueLabel]; track owns drag; render via spec §3.10)

```java
package com.club.ui.component.widget;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.*;
import com.club.ui.text.Align;
import com.club.ui.theme.Tokens;
import static org.lwjgl.glfw.GLFW.*;

public final class Slider extends Container {
    private final float min, max, step;
    private float value;
    private FloatConsumer onChange;
    private boolean showValue = true;
    private final Track track = new Track();
    private final Label valueLabel = new Label("", Tokens.type().label()).align(Align.RIGHT);
    private Row row; private boolean built;

    public Slider(float value, float min, float max, float step) {
        this.min = min; this.max = max; this.step = step; this.value = quantize(value, min, max, step);
    }
    public Slider onChange(FloatConsumer cb) { this.onChange = cb; return this; }
    public Slider showValue(boolean s) { this.showValue = s; built = false; return this; }
    public float value() { return value; }

    static float quantize(float raw, float min, float max, float step) {
        float v = Math.max(min, Math.min(max, raw));
        if (step > 0f) v = min + Math.round((v - min) / step) * step;
        return Math.max(min, Math.min(max, v));
    }
    static String format(float v, float step) {
        boolean integral = step >= 1f && step == Math.rint(step);
        return integral ? Integer.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
    private void setValue(float v) {
        float q = quantize(v, min, max, step);
        if (q != value) { value = q; if (onChange != null) onChange.accept(value); }
    }
    private void build() {
        row = new Row().gap(Tokens.spacing().sm()).crossAlign(CrossAlign.CENTER);
        row.add(track, Sizing.fill());
        if (showValue) row.add(valueLabel);
        children.clear(); children.add(row); built = true;
    }
    @Override public Size measure(float availW, float availH) {
        if (!built) build();
        return row.measure(availW, availH);          // track has min intrinsic width below
    }
    @Override public void layout(float x, float y, float w, float h) { if (!built) build(); super.layout(x, y, w, h); row.layout(x, y, w, h); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (!enabled) return false;
        float d = step > 0f ? step : (max - min) / 100f;
        if (key == GLFW_KEY_LEFT)  { setValue(value - d); return true; }
        if (key == GLFW_KEY_RIGHT) { setValue(value + d); return true; }
        return false;
    }

    /** Interactive track: draws track+fill+knob, owns drag (capture), maps mouseX→value. */
    private final class Track extends Component {
        @Override public Size measure(float a, float b) { return new Size(Tokens.spacing().xxl() * 3, Tokens.spacing().lg()); }
        @Override public boolean mouseClicked(double mx, double my, int btn) { if (btn == 0) { pressed = true; mapTo(mx); return true; } return false; }
        @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { mapTo(mx); return true; }
        @Override public boolean mouseReleased(double mx, double my, int btn) { pressed = false; return true; }
        private void mapTo(double mx) { float frac = (float) ((mx - x) / Math.max(1f, w)); setValue(min + frac * (max - min)); }
        @Override public void render(UiContext ctx) {
            float th = Tokens.border().thickness() * 2, ty = y + (h - th) / 2f, r = th / 2f;
            ctx.renderer().roundedRect(x, ty, w, th, r, Tokens.surface().surfaceHi());
            float frac = (max > min) ? (value - min) / (max - min) : 0f;
            ctx.renderer().roundedRect(x, ty, w * frac, th, r, Tokens.accent().accent());     // FLAT fill (no gradient)
            ctx.renderer().circle(x + w * frac, y + h / 2f, h / 2f - Tokens.border().thickness(), Tokens.accent().accent());
            if (focused) ctx.renderer().border(x, y, w, h, r, Tokens.interaction().focusRingWidth(), Tokens.interaction().focusRing());
        }
    }
    @Override public void render(UiContext ctx) {
        if (showValue) valueLabel.text(format(value, step));
        super.render(ctx);                            // renders the Row (track + valueLabel)
    }
}
```

> **Headless note:** logic-тесты вызывают `showValue(false)` → `Row[track]` без `Label` → `layout`/drag не трогают
> text-`width`. `quantize`/`format` тестируются как чистые статики.

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): Slider widget (flat fill, drag + arrows, value label) [M2.2]"`

---

## Phase 4 — Dev-gallery + acceptance

### Task 12: Gated dev-gallery (visual acceptance; removed before merge)

**Files:**
- Create (gated, temporary): `src/main/java/com/club/ui/devgallery/WidgetGallery.java` (a `Screen` that calls
  `Ui.init()`/`Ui.beginFrame(ctx)`, builds a `Column`/`Card`/`Window` showing every widget in every state, feeds
  `UiContextImpl.setTime(...)`, forwards `mouseClicked/mouseReleased/mouseDragged/mouseMoved/keyPressed/mouseScrolled`).
- Create (gated): keybind/hook to open it (dev-only; **deleted before merge** — see frozen §8.1).

**Interfaces:**
- Consumes: all 10 widgets, `Ui`, `UiContextImpl`, `Column`/`Row`/`Card`/`Window`, `FocusManager`.

- [ ] **Step 1:** Build the gallery screen: sections for resting/hover/press/focus/disabled, Toggle ON/OFF, Button
  PRIMARY/GHOST, Slider with/without value, a `Window` with a `ScrollArea`-wrapped `Column` of `Card`s.
  Wrap disabled exemplars in `pushOpacity(interaction().disabledAlpha())`.
- [ ] **Step 2:** Run client (`./gradlew.bat runClient`), open the gallery, capture screenshots at **1× and zoom**.
- [ ] **Step 3: Visual acceptance** (frozen §8.1/§9): text crisp at 1× and zoom; rounded corners / borders / Toggle
  gradient / active glow / Slider flat fill — **no banding, no stair-steps, no CPU-AA**. Record screenshots in the report.
- [ ] **Step 4:** Confirm `ArchitectureRuleTest` still green (gallery uses only `ctx`-level rendering).
- [ ] **Step 5: Commit** the gallery on the branch (temporary) — `git commit -m "chore(ui): gated widget dev-gallery (temp, removed before merge) [M2.2]"`

### Task 13: Full suite + Milestone Report

- [ ] **Step 1:** `./gradlew.bat test` — **all green** (existing + new widget tests).
- [ ] **Step 2:** `./gradlew.bat build` — green.
- [ ] **Step 3:** Final adversarial code-review on the diff (spec-compliance + quality); fix Critical/Important.
- [ ] **Step 4:** Write **Milestone Report** (format `AI_CONTEXT.md`): что сделано · решения · компромиссы · риски ·
  charter V2 (backend-only · нет low-level вне backend · контракты не менялись · gui/hud не тронуты). Update
  `docs/UI-V2-RISKS.md`/`docs/UI-V2-PERF.md` and `docs/project/CURRENT_TASK.md`.
- [ ] **Step 5:** **Before merge:** delete the gated dev-gallery + `Ui.init()` hook (frozen §8.1). Stop for user
  approve (merge is the user's decision).

---

## Self-Review (план против спеки)

- **Покрытие §3 (10 виджетов):** Label(T2)·Divider(T3)·Panel(T4)·Card(T5)·Window(T7)·ScrollArea(T6)·Button(T8)·
  Toggle(T9)·Checkbox(T10)·Slider(T11) — все есть. Callbacks(T1). onAccent(T0). Галерея+приёмка(T12). ✓
- **§2.8 keyboard (widget-scope):** Button/Toggle/Checkbox SPACE/ENTER, Slider LEFT/RIGHT — в T8–T11 + тесты. ✓
- **§2.4 состояния через `interaction()`:** focus-ring/hover/press/disabled во всех интерактивных. ✓
- **R13:** база гейтит disabled scroll; ScrollArea без своего гарда (T6) — соответствует §3.6/§4. ✓
- **Capture:** Button/Toggle/Checkbox/Slider/Window/ScrollArea — через `pressedChild` (не дублируем CaptureTest). ✓
- **No literals:** все размеры/цвета — токены; px (1px divider / 2px track) = `thickness()`-кратные. ✓
- **Headless-тесты:** чистые helpers (quantize/format/clampOffset) + bounds + stub-дети + Slider `showValue(false)` +
  TitleBar `lineHeight`-only. Text-`width` только в `measure()` (не тестируется). ✓
- **Типы согласованы:** `BoolConsumer`/`FloatConsumer`/`Variant`/`quantize`/`format`/`clampOffset`/`moveBy` — имена едины. ✓
- **Foundation не меняется** (кроме approved G4 onAccent, T0). ✓
