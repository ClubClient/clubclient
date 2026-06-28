# Club — UI V2: Stage 2 Design System Specification

> Формальная спецификация **дизайн-системы** (Tokens + библиотека компонентов), строится ПОВЕРХ контрактов
> Stage 1 (`docs/UI-V2.md` §3–5) и фиксирует/дополняет §6–8. Пояснения — русский, идентификаторы/код — English.
> **Status: APPROVED & FROZEN (2026-06-28).** Архитектура и API Stage 2 заморожены — меняются только при
> серьёзных причинах с обсуждением. Это long-term API: не временные решения, не упрощения. **Значения темы**
> (§3.8) могут эволюционировать через `Theme`/`DESIGN.md` без изменения API Tokens.

---

## 0. Scope & non-goals

**В объёме Stage 2:** полная токен-система (все группы, включая `Palette` и `Elevation`); layout-движок
(Column/Row/Stack/Spacer); motion-каркас; базовая модель компонента + контейнер + фокус/ввод; библиотека
компонентов в две волны (Core → Extended). Решено пользователем: токены — целиком сразу; компоненты — ядром,
затем расширенный набор.

**НЕ в объёме:** ClickGUI (Stage 3), HUD (Stage 4), перенос функционала и ретайр legacy (Stage 5), привязка
`Ui.init()` к реальному клиентскому init (переносится в Stage 3). `gui/*`, `hud/*`, `Theme`, `ClubFont`,
`RenderHelper` **не трогаются**. Контракты рендера Stage 1 (`UiRenderer/UiText/Ui/UiContext`) **не меняются** —
Stage 2 их только **использует** и **дополняет** токен-фасад (`Palette`/`Elevation`).

---

## 1. Governing principles (обязательны для всего Stage 2)

1. **Backend-only.** Любой рендер — только через `ctx.renderer()` / `ctx.text()` (→ фасад `Ui`). Низкоуровневого
   GL/`DrawContext`/`ClubFont`/`RenderHelper` вне `com.club.ui.backend.*` нет. `ArchitectureRuleTest` зелёный.
2. **Tokens — единственный источник правды.** В компонентах и layout запрещены литералы цвета, радиуса, размера,
   отступа, длительности анимации. Всё — из `Tokens`. **Единственное санкционированное место для числовых
   литералов значений — файлы определения тем** (`theme/themes/*`).
3. **Layout декларативен; компоненты position-passive.** Компонент НЕ вычисляет и НЕ выбирает свои координаты.
   Он объявляет только желаемый размер (`measure`) и рисует в пределах bounds, которые ему **назначил**
   `layout(x,y,w,h)`. Поля `x,y,w,h` (§7) — назначаемый layout-системой, read-only для `render()` вход.
   Компонент никогда не позиционирует себя или соседей и не использует абсолютные координаты. (Исключение —
   root-контейнеры, напр. `Window`: их экранную позицию задаёт владеющий `Screen`/перетаскивание, не родительский
   layout.)
4. **Композиционность.** Если компонент собирается из существующих — собирать из них, а не плодить новый сложный
   виджет ради одного места.
5. **Не оптимизировать раньше времени.** Архитектура обязана **допускать** виртуализацию списков, кэширование и
   batching, но НЕ реализует их сейчас. Приоритет — чистота API (см. §11).
6. **Один компонент — одно понятное назначение.** Никакого дублирования ответственности.
7. **Foundation самодостаточен.** Пока M2.1 не завершён, реальные UI-компоненты не пишутся; все компоненты
   используют исключительно готовые Foundation-API.
8. **Theme — единственный источник внешнего вида.** После Stage 2 любое изменение визуального стиля — только
   через `Theme`/`Tokens`. Компоненты не содержат собственных представлений о цвете, глубине, размере, анимации.
9. **DESIGN.md — визуальный канон.** При расхождении кода и DESIGN.md сначала обновляется DESIGN.md, затем —
   значения темы. Не допускать постепенного дрейфа дизайна и реализации.

---

## 2. Milestones & gates

| Milestone | Содержание | Гейт (стоп + отчёт) |
|---|---|---|
| **M2.1 Foundation** | `theme/` (все токены + ClubDark) · `layout/` · `motion/` · `component/` (Component, Container, FocusManager, UiContextImpl) | Foundation полон, тесты зелёные, arch-guard зелёный |
| **M2.2 Core Widgets** | Label · Divider · Panel · Card · Window · ScrollArea · Button · Toggle · Checkbox · Slider · dev-галерея | визуальная приёмка (1×/зум), логика-тесты, ревью |
| **M2.3 Extended Widgets** | Dropdown · TextField · TabBar · Category · SearchBar · Tooltip · Badge · ProgressBar | визуальная приёмка, логика-тесты, ревью |

Каждый milestone завершается Milestone Report (формат из `AI_CONTEXT.md`) и approve перед следующим.
Параллелить субагентами — только внутри milestone на непересекающихся файлах, после approve спеки/плана.

---

## 3. Token System (`com.club.ui.theme`)

### 3.1 Facade & Theme

`Tokens` — статический фасад активной темы (§6, **дополнен** `palette()` и `elevation()`). `Theme` — immutable
бандл всех групп; смена темы = замена инстанса (`setTheme`).

```java
package com.club.ui.theme;

public final class Tokens {
    public static Palette    palette();      // NEW (raw-цвета)
    public static Radius     radius();
    public static Spacing    spacing();
    public static Typography type();
    public static Surface    surface();
    public static Accent     accent();
    public static Border     border();
    public static Shadow     shadow();
    public static Glow       glow();
    public static Elevation  elevation();    // NEW (уровни глубины)
    public static Motion     motion();
    public static void       setTheme(Theme t);
    public static Theme      theme();
}

public record Theme(Palette palette, Radius radius, Spacing spacing, Typography type,
                    Surface surface, Accent accent, Border border, Shadow shadow,
                    Glow glow, Elevation elevation, Motion motion) {}
```

Все группы — immutable records. Доступ: `Tokens.radius().md`, `Tokens.accent().accent`, и т.п.

### 3.2 Palette — raw-слой (единый источник цвета)

Сырые цвета. Семантические группы (Surface/Accent/Border/State) **ссылаются** на Palette — нет дублей hex.
Цвета — упакованный ARGB `int` (`0xAARRGGBB`), хелперы `Color.*` (Stage 1).

```java
public record Palette(
    // neutrals (тёмный→светлее)
    int ink0, int ink1, int ink2, int ink3, int ink4, int ink5, int ink6, int ink7, int ink8,
    // accent
    int accent, int accent2,
    // text ramp
    int textHi, int textMuted, int textDesc, int textFaint,
    // state
    int stateGood, int stateWarn, int stateLow,
    // helper
    int white
) {}
```

### 3.3 Surface / Accent / Border / Shadow / Glow

```java
public record Surface(int bg0, int bg1, int bg2, int surface, int surfaceHi) {}
public record Accent (int accent, int accentHi, int gradientA, int gradientB) {}
public record Border (int subtle, int defaultColor, int strong, float thickness) {}
public record Shadow (Preset sm, Preset md, Preset lg) {
    public record Preset(float dx, float dy, float blur, int color) {}
}
public record Glow   (Preset subtle, Preset active) {
    public record Preset(float size, int color) {}
}
```

> **Glow — styling-нота.** DESIGN.md (legacy-настройка) убрал свечения, т.к. старый glow рисовался кольцами квадов.
> Новый стек делает glow аналитически (SDF-falloff) — §7 контракта задаёт `Toggle ON = акцент + мягкий glow`.
> Структура Glow-токенов фиксируется; **дефолт ClubDark держит glow очень сдержанным** (единственное санкц.
> применение — активный тумблер); значение тюнится темой вплоть до нуля. Это значение, не API.
> **Утверждено (2026-06-28):** glow — инструмент акцента, не декор; максимально мягкий, почти незаметный;
> применяется там, где усиливает восприятие состояния (активный Toggle и отд. акцентные элементы). На приёмке
> M2.2 допускается снижение интенсивности вплоть до нуля **без изменения структуры токенов** (API Glow — навсегда).

### 3.4 Radius / Spacing

```java
public record Radius (float xs, float sm, float md, float lg, float xl) {}
public record Spacing(float xs, float sm, float md, float lg, float xl, float xxl) {}
```

### 3.5 Typography

Роли → `(Weight, size, lineHeight)`. `Weight` — из Stage 1 (`com.club.ui.text.Weight`: REGULAR/MEDIUM/SEMIBOLD).
MSDF resolution-independent → размер свободен (legacy-ограничение «нативный размер» не применяется).

```java
public record Typography(Role display, Role title, Role heading, Role body, Role label, Role caption) {
    public record Role(Weight weight, float size, float lineHeight) {}
}
```

### 3.6 Elevation — уровни глубины (NEW)

Каждый уровень = композиция поверхности + бордера + тени (+ опц. glow). Компонент выбирает уровень и не
пересобирает depth вручную → консистентность.

```java
public record Elevation(Level level0, Level level1, Level level2, Level level3) {
    public record Level(int surface, int border, Shadow.Preset shadow, Glow.Preset glow) {} // glow nullable
}
```

Назначение уровней: `level0` фон страницы · `level1` Card/Panel (resting) · `level2` Window/Dropdown-поповер ·
`level3` Tooltip/modal.

### 3.7 Motion

```java
public record Motion(Durations durations, Easings easings) {
    public record Durations(float instant, float fast, float normal, float slow) {}   // секунды
    public record Easings(Easing standard, Easing decelerate, Easing accelerate, Easing linear) {}
}
```
`Easing` — из `com.club.ui.motion` (§6). Длительности в секундах (совместимо с `UiContext.time()`).

### 3.8 ClubDark — дефолтная тема (значения тюнятся, API заморожен)

Значения из `docs/DESIGN.md` (палитра «v2.5»; navy `#081224`/`#59D0FF` **исключены**). Файл `theme/themes/ClubDark`
— единственное место литералов.

| Группа | Поле | Значение |
|---|---|---|
| Palette.ink | ink0..ink8 | `#06090F #090E16 #0B111A #0C1320 #0F1624 #131B2A #18212F #1D2536 #222A38` |
| Palette.accent | accent / accent2 | `#7CABFF` / `#78D7FF` |
| Palette.text | textHi/Muted/Desc/Faint | `#F4F6FA #A6ADBB #767E8E #5A6273` |
| Palette.state | good/warn/low | `#2ECC71 #E3C66A #E06B6B` |
| Surface | bg0/bg1/bg2/surface/surfaceHi | `#06090F #0B111A #0F1624 #131B2A #18212F` |
| Accent | accent/accentHi/gradA/gradB | `#7CABFF` / `#93BBFF`¹ / `#7CABFF` / `#78D7FF` |
| Border | subtle/default/strong/thickness | white@6% / `#1D2536` / `#2A3550`¹ / `1px` |
| Radius | xs/sm/md/lg/xl | `4 / 6 / 10 / 14¹ / 20¹` |
| Spacing | xs/sm/md/lg/xl/xxl | `4 / 8 / 12 / 16 / 24 / 32`¹ |
| Typography | display/title/heading/body/label/caption | SB·20/22¹ · SB·16/22 · M·15/20 · M·13/18 · M·12/16 · R·12/16 |
| Shadow | sm/md/lg | `(0,1,4,blk@25%) / (0,4,12,blk@30%) / (0,8,24,blk@35%)`¹ |
| Glow | subtle/active | `(6, accent@10%) / (10, accent@18%)`¹ сдержанно |
| Motion.dur | instant/fast/normal/slow | `0 / 0.12 / 0.20 / 0.32` с |

¹ — **принятый стартовый дефолт темы** (утв. 2026-06-28; в DESIGN.md прямого значения нет). Эволюционирует через
`Theme`/`DESIGN.md` без изменения API Tokens. Остальное — прямые значения DESIGN.md.

---

## 4. Layout System (`com.club.ui.layout`)

Утверждённая гибридная модель. Без grid/flexbox/constraints/абсолюта. Контейнеры считают раскладку; компоненты
position-passive (принцип §1.3). Два прохода: **measure** (желаемые размеры) → **layout** (финальные bounds).

### 4.1 Типы

```java
public record Size(float w, float h) {}                       // результат measure()
public sealed interface Sizing {                              // layout-param на МАИН-оси (задаёт родитель)
    record Fixed()           implements Sizing {}             // intrinsic (measured)
    record Fill()            implements Sizing {}             // делит остаток поровну
    record Weight(float w)   implements Sizing {}             // пропорция остатка
    static Sizing fixed();  static Sizing fill();  static Sizing weight(float w);
}
public record Insets(float top, float right, float bottom, float left) {
    static Insets all(float v); static Insets symmetric(float h, float v); static Insets ZERO;
}
public enum CrossAlign { START, CENTER, END, STRETCH }
public enum MainAlign  { START, CENTER, END }                 // распределение, когда дети Fixed
public enum Anchor     { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT }
```

`Sizing` задаётся **родителем** при `add(child, sizing)` — компонент остаётся layout-agnostic (не знает про Fill/Weight).

### 4.2 Контейнеры

```java
public final class Column extends Container {     // вертикальный стек
    Column padding(Insets p); Column gap(float g);            // g — из Tokens.spacing()
    Column crossAlign(CrossAlign a); Column mainAlign(MainAlign a);
    Column add(Component child);                              // Sizing.fixed() по умолчанию
    Column add(Component child, Sizing sizing);
}
public final class Row extends Container { /* симметрично Column по горизонтали */ }

public final class Stack extends Container {      // overlay + anchor (бейджи/тултипы/оверлеи)
    Stack add(Component child, Anchor anchor);
}
public final class Spacer extends Component {     // гибкий/фиксированный зазор
    static Spacer fixed(float px); static Spacer fill(); static Spacer weight(float w);
}
```

Семантика: `Column/Row` мерят детей, раздают остаток main-оси (Fill поровну / Weight пропорционально / Spacer),
выравнивают по cross-оси (`STRETCH` тянет, иначе START/CENTER/END), применяют `padding`/`gap` (всё — токены).

---

## 5. Component model (`com.club.ui.component`)

### 5.1 Component (контракт §7, уточнён)

```java
public abstract class Component {
    protected float x, y, w, h;                  // НАЗНАЧАЮТСЯ layout-системой; read-only для render()
    public boolean enabled = true, visible = true;
    protected boolean hovered, pressed, focused;

    public Size measure(float availW, float availH);         // желаемый размер (intrinsic)
    public void layout(float x, float y, float w, float h);  // назначает bounds (вызывает контейнер)
    public abstract void render(UiContext ctx);              // рисует в пределах bounds через ctx

    public boolean mouseClicked(double mx, double my, int button)  { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    public void    mouseMoved(double mx, double my) {}
    public boolean mouseScrolled(double mx, double my, double amount) { return false; }
    public boolean keyPressed(int key, int scan, int mods) { return false; }
    public boolean charTyped(char ch, int mods) { return false; }
}
```

### 5.2 Container

```java
public abstract class Container extends Component {
    protected final List<Component> children;    // создаётся вне render() (alloc-rule)
    // диспатч ввода: hit-test по bounds → ребёнку в z-порядке; событие "всплывает" (true = поглощено)
    // hover из mouseMoved; render() рисует видимых детей (clip при необходимости)
}
```

### 5.3 FocusManager

Один владелец фокуса на корне дерева: Tab-обход фокусируемых, click-to-focus, маршрутизация
`keyPressed/charTyped` сфокусированному компоненту. Не низкоуровневый — оперирует деревом `Component`.

### 5.4 UiContextImpl & время

`UiContextImpl implements UiContext` (root). `time()` — секунды; значение подаётся per-frame владельцем
(dev-галерея в Stage 2; реальный экран в Stage 3). Без `System`-низкоуровневого рендера, без аллокаций в hot-path.

### 5.5 Interaction state machine

`hovered` ← `mouseMoved` внутри bounds; `pressed` ← mouse-down внутри (сброс на release/leave); `focused` ← через
`FocusManager`; `disabled` ← `!enabled`. Все состояния **визуализируются только токенами** (hover-wash, focus-ring,
alpha) — литералов нет. Переходы анимируются через motion-каркас (§6).

### 5.6 Allocation rule (§7)

`TextStyle`, `TextEffect`, `UiContext`, `Transition`, списки детей — в полях / `static final`. Создание в `render()`
запрещено (per-frame alloc). Hot-path без аллокаций (`docs/UI-V2-PERF.md`).

---

## 6. Motion runtime (`com.club.ui.motion`)

```java
public interface Easing { float apply(float t); }            // t∈[0,1]→[0,1], чистая функция
// реализации: Linear, Standard, Decelerate, Accelerate (curve-функции)

public final class Transition {                              // одно анимируемое скалярное свойство
    Transition(float initial, float duration, Easing easing);
    void  target(float v, float now);    // задать цель (now = ctx.time())
    float value(float now);              // текущее значение (eased); хранится в поле компонента
    boolean animating(float now);
}
```

Компонент держит по одному `Transition` на анимируемое свойство (hover-wash α, knob позиция, press-scale,
focus-ring α). Тайминги/кривые — из `Tokens.motion()`, не хардкод. Аллокация — в полях, не в `render()`.

---

## 7. Component set

API — эскизы контракта (имя, ключевые поля/параметры, варианты, события, композиция). Состояния
hover/press/focus/disabled — у всех интерактивных, через токены. События наружу — колбэки (`onX`).

### 7.1 Core (M2.2)

| Компонент | Назначение | API-эскиз | Композиция |
|---|---|---|---|
| `Label` | текст-примитив | `Label(String, Typography.Role)`; align; effect | leaf (`ctx.text()`) |
| `Divider` | разделитель 1px | `Divider(Axis)`; цвет = `border.subtle`/`palette` | leaf |
| `Panel` | плоская поверхность-группировка (без логики) | `Panel()`; surface+radius; держит 1 child (обычно layout) | контейнер-обёртка |
| `Card` | elevated-поверхность с опц. header/footer | `Card().header(Component).footer(Component)`; Elevation.level1 | Column[header, content, footer] |
| `Window` | root-контейнер: title-bar, drag, chrome, rounded-clip контента | `Window(title)`; Elevation.level2; content=child | chrome Row + ScrollArea(content) |
| `ScrollArea` | прокрутка+клип контента | `ScrollArea(content)`; offset, scrollbar, `mouseScrolled` | clip + 1 child; (виртуализация — §11) |
| `Button` | действие | `Button(label).variant(PRIMARY/GHOST).onClick(r)` | Label внутри |
| `Toggle` | вкл/выкл | `Toggle(value).onChange(b)`; ON=акцент-градиент+сдержанный glow, OFF=`FILL_OFF` | leaf (анимир. knob) |
| `Checkbox` | булев флаг | `Checkbox(value).onChange(b)` | leaf |
| `Slider` | число | `Slider(value,min,max,step).onChange(f)`; трек 2px + плоская акцент-заливка + бегунок + значение | Row[track, Label value] |

### 7.2 Extended (M2.3)

| Компонент | Назначение | API-эскиз | Композиция |
|---|---|---|---|
| `Dropdown` | выбор из списка | `Dropdown(options,selected).onSelect(i)`; header + поповер (rounded-clip, Elevation.level2) | header Row + ScrollArea(Column[items]) |
| `TextField` | ввод текста | `TextField(value,placeholder).onChange(s)`; фокус/каретка/выделение | leaf (фокус, IME — §11) |
| `TabBar` | вкладки | `TabBar(labels,active).onSelect(i)`; индикатор активной (акцент-подчёркивание, градиент разрешён) | Row[tab Labels] + indicator |
| `Category` | titled-контейнер группы настроек | `Category(title).add(...)` | Column[title Label, children] |
| `SearchBar` | поиск | `SearchBar(value).onChange(s)` | Row[icon, TextField, clear Button] |
| `Tooltip` | всплывающая подсказка | `Tooltip(text)`; Elevation.level3; anchor через Stack | Panel + Label |
| `Badge` | счётчик/метка | `Badge(text/count).variant(...)` | Panel + Label |
| `ProgressBar` | прогресс | `ProgressBar(value 0..1)`; трек + плоская акцент-заливка | leaf |

Композиционность (§1.4): `SearchBar/Category/Window/Card/Tooltip/Badge` собираются из примитивов; новый сложный
leaf создаётся только если из существующих не собрать.

---

## 8. Verification & testing

### 8.1 Dev-галерея (gated, удаляется перед merge)

Каталог-экран всех компонентов на всех состояниях (по прецеденту Stage 1 acceptance-screen). **Инструмент
разработки, не часть архитектуры клиента**: gated, вне `main`-сборки, удаляется перед merge. Здесь же — вызов
`Ui.init()` (регистрация шейдеров) только на время проверки. Скриншоты 1× и зум.

### 8.2 Тесты (чистые, headless, без GL)

- **Layout-математика** (главная поверхность): golden-позиции Column/Row для Fixed/Fill/Weight, Spacer,
  padding/gap, CrossAlign (вкл. STRETCH), MainAlign; Stack-anchor.
- **Motion:** easing-кривые (границы 0/1, монотонность), сходимость `Transition`.
- **Tokens:** полнота темы (нет невалидных), резолв Palette-ссылок, Elevation-уровни заданы.
- **Component-логика:** state-переходы, hit-test/диспатч ввода контейнером, фокус-обход, срабатывание колбэков.
- **Arch-guard:** `ArchitectureRuleTest` остаётся зелёным.

---

## 9. Acceptance criteria

**M2.1 Foundation:** все токен-группы + ClubDark существуют и доступны через `Tokens`; layout-движок проходит
golden-тесты; motion-каркас проходит тесты; `Component/Container/FocusManager/UiContextImpl` существуют;
arch-guard зелёный; реальных виджетов ещё нет. Foundation самодостаточен.

**M2.2 Core / M2.3 Extended:** каждый компонент рисует только через `ctx`; никаких литералов (только токены);
position-passive соблюдён; логика покрыта тестами; dev-галерея демонстрирует все состояния резко на 1× и зуме
(нет banding/ступеней/CPU-AA); ревью (adversarial на финале milestone) без Critical/Important.

---

## 10. Charter V2 compliance

- **backend-only** ✓ — рендер только через `ctx.renderer()/text()`.
- **нет low-level вне backend** ✓ — гарантирует `ArchitectureRuleTest`.
- **контракты Stage 1 не менялись** ✓ — `UiRenderer/UiText/Ui/UiContext` нетронуты; добавлены только `Palette`/
  `Elevation` в токен-фасад (§6 расширение, утверждено) + новые слои `layout/motion/component`.
- **gui/hud не тронуты** ✓.

После approve спецификации — архитектура Stage 2 **заморожена** (изменения только при серьёзных причинах,
с обсуждением).

---

## 11. Future-proofing (заложено, НЕ реализуется в Stage 2)

Архитектура **допускает**, но Stage 2 **не реализует** (приоритет — чистота API):

- **Виртуализация списков:** `ScrollArea`/`Dropdown` структурированы так, что позже можно рисовать только видимые
  дети (контент измеряем, offset применяется) — сейчас рисуются все.
- **Кэширование раскладки:** measure/layout разделены, результат можно кэшировать при неизменных входах — сейчас
  считается каждый кадр.
- **Batching:** текст уже имеет batching-seams со Stage 1; фигуры можно батчить позже — сейчас прямые вызовы.

Эти заделы не должны усложнять текущий API. Преждевременной реализации нет.

---

## 12. Styling-решения (значения, не API; утв. 2026-06-28)

- **Glow** (§3.3): принят сдержанный дефолт (инструмент акцента, не декор); финальная подстройка / возможный ноль —
  по визуальной приёмке M2.2, без изменения структуры токенов.
- **accentHi / border.strong / Radius lg-xl / Spacing-шкала / Shadow-пресеты / display-роль** (¹ в §3.8): приняты
  как стартовые значения темы; эволюционируют через `Theme`/`DESIGN.md` без изменения API.
