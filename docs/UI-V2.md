# Club — UI V2: Render & Architecture Specification

> Долговечная спецификация **контрактов** нового UI-стека (рендер + ядро). Это фундамент:
> компоненты, ClickGUI и HUD строятся ПОВЕРХ этих контрактов. До фиксации контрактов кода нет.
> Пояснения — на русском, идентификаторы/код — English. Статус: **APPROVED — контракты Stage 1 зафиксированы (2026-06-25).**

## 0. Цель и не-цели

**Цель:** заменить фундаментальный потолок текущего рендера (`DrawContext.fill` + bitmap-`TextRenderer`)
стеком, дающим качество уровня современных оверлеев (ориентир: Pulse Visual / Raycast / Linear /
премиальные игровые лаунчеры). Технически потолок уже доказан PoC-ом (MSDF + аналитический SDF).

**Не-цели этого документа:** конкретные цвета/размеры/раскладки, визуальный стиль клиента, перенос
существующего функционала. Это решается позже (Stage 2+). Здесь — только **контракты и архитектура**.

**Запрещено в новом стеке (ModernBackend):** ванильный `TextRenderer` как основной текст; CPU-AA;
пиксельный `fill`; glow из полупрозрачных прямоугольников/концентрических квадратов; `radialGlow` из
старого `RenderHelper`; banding-градиенты; литералы цвета/размера внутри компонентов.

## 1. Порядок работ (charter V2)

| Stage | Содержание | Гейт |
|------|------------|------|
| **1** | Рендер-фундамент: `UiRenderer`, `UiText`, `ModernBackend`, `LegacyBackend`, `Ui`-фасад, MSDF-атлас+тулинг, приёмочный экран | этот документ + план |
| 2 | Дизайн-система: `Tokens` + библиотека компонентов | спека компонентов (раздел 7–8) |
| 3 | Новый ClickGUI на компонентах | — |
| 4 | Новые HUD | — |
| 5 | Перенос существующего функционала, ретайр legacy | — |

**Правило:** существующий `gui/*`, `hud/*`, `Theme`, `ClubFont`, `RenderHelper` **не трогаются до Stage 5**.
Новый стек живёт в `com.club.ui` параллельно. Никаких косметических правок старого UI до завершения Stage 1.

## 2. Системные соглашения (общие для всех контрактов)

- **Координаты:** float, в GUI-scaled пикселях (та же система, что `DrawContext`), origin top-left, y вниз.
- **Матрица:** всё рисуется через текущую матрицу `DrawContext` → масштаб/зум/GUI-scale применяются
  автоматически; качество не деградирует (SDF/MSDF считают AA в экранном пространстве через `fwidth`).
- **Цвет:** упакованный ARGB `int` (`0xAARRGGBB`). Хелперы: `Color.rgba(r,g,b,a)`, `Color.withAlpha(c,a)`,
  `Color.lerp(a,b,t)`. Внутри компонентов цвет берётся ТОЛЬКО из токенов.
- **Единицы:** все размеры/радиусы/толщины — px (float). Без «магических» констант в вызовах компонентов.
- **Resolution independence:** обязательна для ModernBackend на всех методах фигур и текста.
- **Поток:** методы вызываются на render-потоке внутри `Screen#render` / `HudRenderCallback`.

## 3. Контракт `UiRenderer` (фигуры, тени, свечение, клиппинг)

```java
package com.club.ui;

public interface UiRenderer {

    // --- прямоугольники / скругления ---
    void rect(float x, float y, float w, float h, int color);
    void roundedRect(float x, float y, float w, float h, float radius, int color);
    void roundedRect(float x, float y, float w, float h, Radii radii, int color); // per-corner
    void border(float x, float y, float w, float h, float radius, float thickness, int color);

    // --- градиенты (GPU, без banding, с дизерингом) ---
    void gradient(float x, float y, float w, float h, float radius,
                  int colorA, int colorB, Axis axis);              // 2-stop linear
    void gradient(float x, float y, float w, float h, float radius,
                  int[] stops, float[] positions, Axis axis);      // N-stop (опц., может быть Stage 2)

    // --- тень / свечение (через distance field, не из квадов) ---
    void shadow(float x, float y, float w, float h, float radius,
                float dx, float dy, float blur, int color);
    void glow(float x, float y, float w, float h, float radius,
              float size, int color);

    // --- примитивы ---
    void line(float x1, float y1, float x2, float y2, float thickness, int color);
    void circle(float cx, float cy, float r, int color);

    // --- clipping (стек) — оба метода в контракте Stage 1 ---
    void pushClip(float x, float y, float w, float h);
    void pushRoundedClip(float x, float y, float w, float h, float radius);
    void popClip();

    // --- глобальная прозрачность (стек) ---
    void pushOpacity(float multiplier);
    void popOpacity();

    // --- метаданные бэкенда ---
    boolean isResolutionIndependent();
}
```

Вспомогательные типы: `enum Axis { HORIZONTAL, VERTICAL }`, `record Radii(float tl, float tr, float br, float bl)`.

**Семантика (обязательна для ModernBackend):**
- `roundedRect/border` — аналитический `sdRoundBox`, AA в экранном пространстве; идеально при любом зуме.
- `gradient` — интерполяция на GPU + ordered-dither (нет banding/ступеней). `Axis` = ось.
- `shadow` — мягкое смещённое гало из distance-функции (`dx,dy` смещение, `blur` радиус размытия).
- `glow` — наружное непрерывное гало шириной `size` (falloff), НЕ из прямоугольников.
- `pushClip` — прямоугольный клип через стек GL-scissor. `pushRoundedClip` — клип со скруглением для
  контейнеров (Window/Card/Dropdown-поповер); **оба метода присутствуют в контракте Stage 1**, чтобы не
  менять контракты компонентов/контейнеров позже. Первая реализация `pushRoundedClip` может быть упрощённой
  (например, прямоугольный clip + SDF-маска по краям), но API и семантика фиксируются сейчас. Вложенность =
  пересечение; `popClip` обязателен парно для обоих.
- `pushOpacity` — множитель альфы для всего, что рисуется до `popOpacity` (для fade/анимаций).
- Все методы — no-op при `w<=0 || h<=0`.

**LegacyBackend** реализует тот же интерфейс, но допускает аппроксимацию (CPU-AA скругления, glow-кольца,
прямоугольный клип без rounded) и возвращает `isResolutionIndependent() == false`. Только fallback.

## 4. Контракт `UiText` (текст, метрики, перенос, выравнивание, эффекты)

```java
package com.club.ui;

public interface UiText {

    float draw(String text, float x, float y, TextStyle style);   // returns end-x (advance)
    void  drawWrapped(String text, float x, float y, float maxWidth, TextStyle style);

    float width(String text, Weight weight, float size);
    float ascent(Weight weight, float size);
    float descent(Weight weight, float size);
    float lineHeight(Weight weight, float size);

    java.util.List<String> wrap(String text, Weight weight, float size, float maxWidth);

    boolean isResolutionIndependent();
}
```

Типы:
```java
enum Weight { REGULAR, MEDIUM, SEMIBOLD }     // веса Inter (расширяемо)
enum Align  { LEFT, CENTER, RIGHT }

final class TextStyle {                         // immutable + builder
    Weight weight; float size; int color; Align align;
    TextEffect effect;                          // NONE по умолчанию
}

sealed interface TextEffect {
    record None() implements TextEffect {}
    record Outline(float widthPx, int color) implements TextEffect {}
    record Shadow(float dx, float dy, float softness, int color) implements TextEffect {}
    record Glow(float radius, int color) implements TextEffect {}
}
```

**Семантика (обязательна для ModernBackend):**
- Текст рендерится **MSDF**: абсолютная резкость при любом масштабе; ванильный `TextRenderer` НЕ используется.
- Веса — отдельные MSDF-атласы Inter (Regular/Medium/SemiBold), генерятся офлайн.
- `draw` — `(x,y)` = top-left строки; baseline считается из метрик атласа. `align` относительно `x`
  (для CENTER/RIGHT нужен либо `width`, либо вызов внутри известной ширины — детализируется в реализации).
- `effect` — outline/shadow/glow получаются из той же distance-функции (требование ТЗ), без двойной растеризации.
- `wrap`/`drawWrapped` — перенос по `maxWidth` (по словам, с фолбэком на разрыв сверхдлинного слова).
- Метрики (`width/ascent/descent/lineHeight`) — из MSDF-JSON; стабильны и dpi-независимы.
- Глиф вне атласа → фолбэк-символ; при включённом LegacyText — ванильный фолбэк (для не-латиницы Stage 5+).

**LegacyText** — обёртка над `ClubFont`/ванилью; `isResolutionIndependent() == false`. Только fallback.

## 5. Backend Contract (выбор/фолбэк)

```java
package com.club.ui;

public final class Ui {
    public static UiRenderer renderer();         // активный бэкенд
    public static UiText     text();
    public static Backend    backend();
    public static void       setBackend(Backend b);
    public static boolean    modernAvailable();  // шейдеры скомпилированы И атлас загружен
}
enum Backend { MODERN, LEGACY }
```

- Компоненты кодируются **только** против `UiRenderer`/`UiText` через `Ui.renderer()`/`Ui.text()`.

> **ОБЯЗАТЕЛЬНОЕ АРХИТЕКТУРНОЕ ПРАВИЛО (hard rule).** За пределами пакета бэкенда (`com.club.ui.backend.*`)
> ЗАПРЕЩЕНЫ любые прямые низкоуровневые рендер-вызовы: `DrawContext.fill(...)`, `DrawContext.drawText(...)`/
> `context.drawText(...)`, `RenderSystem.*`, `BufferBuilder`/`Tessellator`/`BufferRenderer`, `GlUniform`,
> прямые `ShaderProgram`, а также `ClubFont`/`RenderHelper`. Любой рендер компонентов, контейнеров, ClickGUI и
> HUD проходит ИСКЛЮЧИТЕЛЬНО через `Ui.renderer()` и `Ui.text()`. Низкоуровневый код инкапсулирован только в
> `ModernBackend`/`LegacyBackend`. Цель — чтобы кодовая база не начала обходить новый стек напрямую; нарушение
> этого правила = регресс архитектуры.
- По умолчанию `MODERN`. Если `modernAvailable() == false` (шейдер не скомпилировался / атлас не загрузился)
  — `Ui` автоматически выбирает `LEGACY` и логирует один WARN. Клиент не должен падать.
- `ModernBackend` гарантирует ПОЛНЫЙ контракт (resolution-independent). `LegacyBackend` — best-effort fallback.
- Переключение `setBackend` — для отладки/сравнения (приёмочный экран Stage 1).

## 6. Token System (Stage 2 — контракт фиксируем сейчас)

Все значения — централизованно и типизированно. **В компонентах запрещены** `new Color(...)`, `radius = 7`,
`padding = 13` и любые литералы цвета/размера.

```java
package com.club.ui.theme;

public final class Tokens {            // активная тема (immutable); тема сменяема
    public static Radius     radius();
    public static Spacing    spacing();
    public static Typography type();
    public static Surface    surface();
    public static Accent     accent();
    public static Border     border();
    public static Shadow     shadow();
    public static Glow       glow();
    public static Motion     motion();
    public static void       setTheme(Theme t);
}
```

Категории (структура, без конкретных значений — значения = Stage 2):
- **Radius:** `xs, sm, md, lg, xl` (px).
- **Spacing:** `xs, sm, md, lg, xl, xxl` (px) — единая шкала отступов.
- **Typography:** роли → `(Weight, size, lineHeight)`: `display, title, heading, body, label, caption`.
- **Surface:** `bg0, bg1, bg2, surface, surfaceHi` (цвета фонов/поверхностей).
- **Accent:** `accent, accentHi, gradientA, gradientB`.
- **Border:** `subtle, default, strong` (цвет + дефолтная толщина).
- **Shadow:** пресеты `(dx, dy, blur, color)`: `sm, md, lg`.
- **Glow:** пресеты `(size, color)`: `subtle, active`.
- **Motion:** `durations` + `easings` (для анимаций компонентов).

Правило доступа: `Tokens.radius().md`, `Tokens.accent().accent` и т.п. `Theme` — набор всех значений;
смена темы — заменой инстанса (основа под будущие темы/варианты).

## 7. Component Contract (Stage 2 — контракт фиксируем сейчас)

Базовая модель компонента: измеряемый, раскладываемый, рисуемый через `UiContext`, обрабатывающий ввод,
с состояниями, событиями и композицией детей.

```java
package com.club.ui.component;

public abstract class Component {
    protected float x, y, w, h;                 // bounds (px), задаются раскладкой

    public Size  measure(float availW, float availH);   // желаемый размер
    public void  layout(float x, float y, float w, float h);

    public abstract void render(UiContext ctx);          // рисует через ctx.renderer()/text()/tokens

    // ввод (возвращают true, если событие поглощено)
    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseReleased(double mx, double my, int button){ return false; }
    public void    mouseMoved(double mx, double my) {}
    public boolean mouseScrolled(double mx, double my, double amount){ return false; }
    public boolean keyPressed(int key, int scan, int mods) { return false; }
    public boolean charTyped(char ch, int mods) { return false; }

    // состояние
    public boolean enabled = true, visible = true;
    protected boolean hovered, pressed, focused;          // визуализируются через токены
}

public interface UiContext {
    UiRenderer renderer();
    UiText     text();
    float      time();        // секундный таймер для анимаций
}
```

- Контейнеры (`Card`, `Section`, `Window`, `Tab`) держат детей и раскладывают их; ввод диспатчится детям.
- Состояния (`hovered/pressed/focused/disabled`) визуализируются исключительно токенами (wash, focus-ring,
  alpha). Никаких литералов.
- События наружу — через колбэки/листенеры (`onClick`, `onChange`, `onSelect`).

**Минимальный набор (Stage 2), эскиз API:**
- `Button` — `label`, `variant {PRIMARY, GHOST}`, `onClick`. Состояния hover/press/disabled.
- `Toggle` — `value`, `onChange(boolean)`. ON = акцент-градиент + мягкий glow; OFF = нейтральный.
- `Slider` — `value, min, max, step`, `onChange(float)`. Трек + заливка + бегунок + значение.
- `Dropdown` — `options, selected, onSelect(i)`. Header + поповер-список (через rounded-clip).
- `Checkbox` — `value, onChange(boolean)`.
- `Card` — поверхность + опц. header/footer + дети (глубина: surface + border + shadow).
- `Section` — заголовок + группа контролов (вертикальный стек со spacing-токенами).
- `Window` — корневой контейнер: title-bar, перетаскивание, chrome, дети, rounded-clip контента.
- `Tab` — набор вкладок, `active, onSelect(i)`; индикатор активной.
- `TextField` — `value, placeholder, onChange`, фокус/каретка/выделение.

## 8. Asset & Build Contract (Stage 1)

- **MSDF-атлас:** офлайн-генерация `msdf-atlas-gen` (pinned версия+checksum), веса Inter Regular/Medium/SemiBold.
  Формализуется Gradle-задачей `genMsdfAtlas` (в PoC делалось вручную). Артефакты (PNG+JSON) — в ресурсах
  `assets/club/ui/font/msdf/`. Рантайм-нативов нет.
- **Charset (фиксируется один раз, дальше не трогаем):** **Latin + Cyrillic + Punctuation + Basic symbols**.
  Генерация Inter R/M/SemiBold выполняется единожды и замораживается — чтобы не возвращаться к атласам на
  поздних этапах и не ломать совместимость компонентов. (Атлас станет крупнее/возможно многостраничным —
  это закладывается сразу в `genMsdfAtlas` и `MsdfFont`.)
- **Шейдеры:** core-шейдеры `ui_sdf_shape` и `ui_msdf_text` под `assets/club/shaders/core/`, регистрация через
  Fabric `CoreShaderRegistrationCallback`. Контракт ModernBackend завязан на их успешную компиляцию
  (иначе fallback на LEGACY).
- **Промоут PoC:** `PocRenderer/MsdfFont/PocText/PocShaders` → `com.club.ui.backend.Modern*`; PoC-экраны
  становятся приёмочными.

## 9. Stage 1 — критерии приёмки

- `UiRenderer`/`UiText`/`Ui` существуют; компоненты возможно писать только против интерфейсов.
- `ModernBackend` реализует контракт раздела 3–4: rounded rect/border, 2-stop gradient (H/V), shadow, glow,
  прямоугольный clip, и текст с outline/shadow/glow. `LegacyBackend` даёт работающий fallback.
- Приёмочный экран демонстрирует каждый метод на 1× и 500% зуме: нет banding/ступеней/CPU-AA в MODERN.
- Переключение `Ui.setBackend(MODERN|LEGACY)` работает; авто-fallback при недоступности MODERN.
- Существующий клиентский UI (`gui/*`, `hud/*`) не затронут; всё gated/изолировано в `com.club.ui`.

## 10. Решения (утверждено 2026-06-25)

1. **Градиенты:** 2-stop в Stage 1; N-stop — Stage 2.
2. **Clipping:** `pushClip` + `pushRoundedClip` + `popClip` — **в контракте Stage 1** (rounded может быть упрощён в
   первой реализации, но API фиксируется сейчас — см. раздел 3).
3. **Align CENTER/RIGHT:** через `UiText.width()`/метрики и позиционирование в компонентах; `UiText` не усложняем.
4. **Charset:** **Latin + Cyrillic + Punctuation + Basic symbols** генерируется в Stage 1 один раз и
   замораживается (см. раздел 8). НЕ откладывается.
5. **Motion/анимации:** каркас и токены — со Stage 2, не в Stage 1.
6. **LegacyBackend:** исключительно fallback-слой.
7. **Hard rule:** вне `com.club.ui.backend.*` запрещены любые низкоуровневые рендер-вызовы; рендер только через
   `Ui.renderer()`/`Ui.text()` (см. раздел 5).
