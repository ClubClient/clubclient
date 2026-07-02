# Club — UI V2: Stage 2 / M2.2 Core Widgets — API Specification

> Уточнение API **core-виджетов** поверх M2.1 Foundation. Строится на frozen `docs/UI-V2-STAGE2-SPEC.md`
> (§7.1 даёт API-эскизы — здесь они доводятся до точного контракта) и контрактах Stage 1 (`docs/UI-V2.md`).
> Пояснения — русский, идентификаторы/код — English.
>
> **Status: APPROVED — публичный Widget API §3 ЗАМОРОЖЕН (2026-06-28).** Ветка `feat/ui-v2-m2.2-core`. Сверено с
> фактической смерженной инфраструктурой (infra-prep commit `23ff2c3`): pointer capture = минимальная
> `pressedChild`-цепочка (**без** auto-release); R13 — на уровне базового `Container`; state-визуал — через
> `Tokens.interaction()`. **Утверждённые решения (2026-06-28):** Window = opt-in `ScrollArea` (Window = только chrome);
> **`Accent.onAccent` добавлен в токены (G4)** → Button PRIMARY-текст = `accent().onAccent()` (§3.7); capture
> auto-release **не добавляем** — остаётся Stage-3 improvement (§10.2); клавиатура — **widget-scope**: `Space`/`Enter`
> (Button/Toggle/Checkbox), `Left`/`Right` (Slider) (§2.8). Далее: план M2.2 → реализация.
>
> **АМЕНДМЕНТ (2026-07-02), фиксация фактических отступлений от frozen §3:**
> 1. **Toggle плоский** — accent-градиент + `glow().active()` из §3.8 сняты в `f40c887` (Stage 6) в пользу
>    плоского языка Variant D (`UI-V2-MENU.md` §4: «без shadow/blur/glow/градиентов»): ON = плоская
>    `accent()`-заливка через `WidgetPaint.surface`, OFF = `controlTrack`. Снятие glow было предодобрено
>    (Stage-2 spec §3.3 «вплоть до нуля»); снятие градиента фиксируется этим амендментом — **не «чинить» обратно**.
> 2. **Неиспользуемые виджеты удалены (2026-07-02):** `Window`, `ModuleCard`, `CategoryItem`, `TextField`,
>    `Card`, `Panel`, `Keybind`, `Divider` (+ их тесты; + мёртвые `WidgetPaint.elevation()/flatSurface()/`
>    `clipRounded()/ACCENT_RIM`) — боевое меню (`ui.menu.ClubMenuScreen`) рисует окно/карточки/rail/поиск
>    экранными композитами и ни один из них не инстанцирует. Живой Widget API: `Label`, `ScrollArea`,
>    `Button`, `Toggle`, `Checkbox`, `Slider`, `Dropdown` (+ база `Control`, `WidgetPaint`,
>    `BoolConsumer`/`FloatConsumer`). Восстановление — из git (состояние до этой чистки).

---

## 0. Scope & non-goals

**В объёме M2.2:** 10 core-виджетов — `Label`, `Divider`, `Panel`, `Card`, `Window`, `ScrollArea`, `Button`,
`Toggle`, `Checkbox`, `Slider` — в новом пакете `com.club.ui.component.widget`; минимальные callback-типы для
событий; gated dev-галерея для визуальной приёмки. **Foundation-доработки (pointer capture, `Interaction`-токены,
R13) уже сделаны отдельным infra-prep-этапом (commit `23ff2c3`) и в объём M2.2 НЕ входят** (см. §F — база, на
которой строятся виджеты; виджеты её не меняют).

**НЕ в объёме:** Extended-виджеты M2.3 (Dropdown/TextField/TabBar/Category/SearchBar/Tooltip/Badge/ProgressBar);
ClickGUI (Stage 3); HUD (Stage 4); привязка `Ui.init()` к реальному клиентскому init (Stage 3 — в M2.2 `Ui.init()`
вызывает только dev-галерея на время проверки и удаляется перед merge). `gui/*`, `hud/*`, `Theme`, `ClubFont`,
`RenderHelper` **не трогаются**. Виртуализация/кэш/batching — заложены, **не реализуются** (frozen §11).

---

## 1. Governing principles (наследуются из frozen §1, ключевое)

1. **Backend-only.** Любой рендер — только через `ctx.renderer()` / `ctx.text()`. Метрики текста при `measure()` —
   через `Ui.text()` (санкционированный фасад, §2.6). Низкоуровневого GL/`DrawContext`/`ClubFont`/`RenderHelper`
   вне `backend/` нет. `ArchitectureRuleTest` остаётся зелёным.
2. **Tokens — единственный источник правды.** В виджетах запрещены литералы цвета/радиуса/размера/отступа/
   длительности. Всё — из `Tokens.*`. Где дизайн требует конкретный px (1px divider, 2px track), он выражается
   через токен-выражение (`border().thickness()` и его кратные), а не через литерал.
3. **Position-passive.** Виджет объявляет только желаемый размер (`measure`) и рисует в пределах назначенных
   `layout(x,y,w,h)` bounds. Исключение — `Window` (root-контейнер: свою экранную позицию держит сам / меняет
   drag-ом, §3.5).
4. **Композиционность.** Составные виджеты (`Card`, `Window`, `Slider`) собираются из примитивов и существующих
   layout-контейнеров (`Column`/`Row`/`Stack`), а не дублируют layout/render/input/animation.
5. **Один виджет — одно назначение.** Никакого дублирования ответственности.
6. **Alloc-free hot path.** `TextStyle`/`Transition`/callbacks/дети — в полях, не в `render()`/`measure()`.
   (frozen §5.6.)

---

## F. Foundation baseline — pointer capture + Interaction tokens + R13 (DONE, commit `23ff2c3`)

> **Эти доработки уже реализованы и смержены как отдельный infra-prep-этап (commit `23ff2c3`, 76 тестов 0 падений,
> arch-guard зелёный) — НЕ часть работы M2.2.** Раздел описывает фактическую базу, на которой строятся виджеты.
> Источник истины — `docs/UI-V2-STAGE2-SPEC.md` §3.9/§5.1/§5.2/§5.5/§5.7. **Виджеты НЕ меняют эту базу**; любые
> новые инфраструктурные изменения — только с отдельным approve.
>
> **+ G4 (утв. при approve M2.2-спеки, 2026-06-28):** в `Accent` добавлен аддитивный токен `onAccent` (=`ink0`;
> контрастный передний план на accent-заливке, для Button PRIMARY) — frozen-спека §3.3/§3.8/§10. Чисто аддитивно
> (рендер-контракты Stage 1 не затронуты); коммитится как infra перед виджетами. Прочую базу M2.2 не меняет.

Capture (G1) закрыл пробел доставки `mouseReleased`/drag для press/drag-виджетов (`Slider`, `Window`,
`ScrollArea`-thumb, `Button` cancel-on-release). Модель — **`pressedChild`-цепочка по `Container`** (vanilla
`ParentElement`); отдельный глобальный `PointerCaptureManager` НЕ вводится (для оверлеев/порталов M2.3 надстроится
поверх без слома API).

### F.1 `Component` — добавлен один метод (аддитивно)

```java
// рядом с mouseReleased/mouseMoved; сигнатуры существующих методов не менялись
/** Delivered only to the capture owner (the press-consuming component); see Container's pressedChild routing. */
public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
```

`mouseReleased(double,double,int)` уже существовал в контракте — теперь он реально доставляется. Дельты включены в
сигнатуру (большинству drag-виджетов нужна именно дельта); порядок аргументов совместим с ванильным `Screen`.

### F.2 `Container` — фактическая реализация (committed)

```java
private Component pressedChild;   // child that consumed the active press = this level's capture head; null when idle

@Override public boolean mouseClicked(double mx, double my, int button) {
    for (int i = children.size() - 1; i >= 0; i--) {
        Component c = children.get(i);
        if (c.visible && c.enabled && c.contains(mx, my) && c.mouseClicked(mx, my, button)) {
            pressedChild = c; return true;        // capture the consumer for subsequent drag/release
        }
    }
    return false;
}

/** Routed only to the capture owner (never hit-test, never broadcast); clears capture at this level. */
@Override public boolean mouseReleased(double mx, double my, int button) {
    Component p = pressedChild;
    pressedChild = null;
    return p != null && p.mouseReleased(mx, my, button);
}

/** Routed only to the capture owner — delivered even when the cursor has left its bounds. */
@Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
    return pressedChild != null && pressedChild.mouseDragged(mx, my, button, dx, dy);
}
```

### F.3 Свойства и обвязка (фактические)

- **Routing по ссылке** (`pressedChild`-цепочка от корня до листа), не hit-test → drag/release доходят, **даже когда
  курсор ушёл за пределы** виджета.
- **Освобождение capture — на `mouseReleased`** (очищается на каждом уровне при разворачивании рекурсии). Без
  broadcast-release. **Авто-free на `!visible`/`!enabled`/detach во время drag НЕ реализован** (жест короткий,
  обрамлён press→release; см. §10 — потенциальное усиление безопасности — отдельный infra-approve, не M2.2).
- **Рекурсия естественная**: если `pressedChild` сам `Container` — он маршрутизирует своему `pressedChild` (вложенный
  drag, см. `Window` §3.5). Drag-ручка (`Window` title-bar) — это **захватываемый ребёнок**: она поглощает press и в
  своём `mouseDragged` двигает владельца. Никаких override диспатча/флагов в самом `Window` — всё по ссылке (§3.5).
- **`disabled`-ребёнок не захватывается** (enabled-чек в press-диспатче). **R13:** `Container.mouseScrolled` тоже
  уважает `enabled` — единообразно с click/capture (база изменена в `23ff2c3`).
- **Interaction-токены (G2):** `Tokens.interaction()` → `hoverWash()/pressOverlay()/disabledAlpha()/focusRing()/
  focusRingWidth()` (см. §2.7) — state-визуал виджетов берётся отсюда, без литералов.
- **`FocusManager`** (клавиатура) не тронут. `UiContext`/`UiRenderer`/`UiText`/`layout`/`motion`-каркас — **не менялись**.
- **Root-обвязка вне Foundation** (dev-галерея в M2.2, Stage-3 `Screen` потом): форвардит MC-события —
  `mouseDragged → root.mouseDragged`, `mouseReleased → root.mouseReleased`, `mouseMoved → root.mouseMoved`
  (hover, как сейчас). Различение move/drag — по факту удержания кнопки (MC шлёт `mouseDragged`, пока кнопка нажата).
- **Тесты (уже есть, committed):** `component/CaptureTest.java` — press→drag→release доходит до листа (вкл. через
  вложенные контейнеры); off-bounds-доставка; capture очищается после release; disabled/declined-press не
  захватываются. `ContainerTest` — R13 (disabled не скроллит). `TokensTest` — `Interaction`-токены ссылаются на
  Palette/Accent. `ArchitectureRuleTest` зелёный.

---

## 2. Common widget conventions

### 2.1 Размещение и базовые классы
Все виджеты — в `com.club.ui.component.widget`. Leaf-виджеты расширяют `Component`; составные — `Container`
(используя внутри `Column`/`Row`/`Stack`). Никаких прочих публичных API не добавляется, кроме виджетов и
callback-типов (§2.2). Контейнеры с одним child (`Panel`/`Card`/`Window`) корректно работают при отсутствии
содержимого: `measure`/`layout`/`render` строят только поверхность (контент-вклад = 0), без NPE.

### 2.2 Callbacks (APPROVED — минимальные типы в `component/widget`)
Чтобы не боксить на действиях пользователя и не тянуть зависимости:
```java
@FunctionalInterface public interface BoolConsumer  { void accept(boolean value); }   // Toggle/Checkbox.onChange
@FunctionalInterface public interface FloatConsumer { void accept(float value); }      // Slider.onChange
// Button.onClick — java.lang.Runnable (JDK)
```
Callback хранится в поле виджета (`null` = нет подписчика; вызов под guard `if (cb != null)`). Это не «новая
система» — это типы событий слоя виджетов.

### 2.3 Builder-стиль
Лаконичные fluent-сеттеры (`return this`), как у `Column`/`Row` в Foundation. Конструктор — обязательные данные;
сеттеры — опции. Тип возврата сеттера — конкретный класс виджета.

### 2.4 Состояния (frozen §5.5)
`hovered` ← `mouseMoved` внутри bounds (broadcast от `Container`); `pressed` ← press внутри, держится через capture
(`pressedChild`), надёжно сбрасывается на release; `focused` ← `FocusManager`; `disabled` ← `!enabled`. Все состояния
визуализируются **только токенами `Tokens.interaction()`** (§2.7): hover — overlay `hoverWash()`; press —
`pressOverlay()` (опц. press-scale через `Transition`); focus — ring `focusRing()`/`focusRingWidth()`; disabled —
`renderer().pushOpacity(interaction().disabledAlpha())`. **Никаких литеральных α/цветов состояний в виджетах.**
Переходы — через `Transition` (§2.5).

### 2.5 Motion
Каждое анимируемое скалярное свойство = один `Transition` в поле виджета (hover-wash α, knob-позиция Toggle/Slider,
check-in Checkbox, press-feedback). Тайминги/кривые — `Tokens.motion()` (`durations().fast()` для hover/press,
`durations().normal()` для knob; `easings().standard()`/`decelerate()`), не хардкод. `target(v, ctx.time())` в
обработчике события; `value(ctx.time())` в `render()`.

### 2.6 measure() и метрики текста
`Component.measure(availW, availH)` не получает `UiContext` (frozen-сигнатура). Текст-зависимые виджеты
(`Label`, `Button`, `Slider` value-label) берут метрики через статический фасад `Ui.text()`
(`width(text,weight,size)`, `lineHeight(weight,size)`) — это санкционировано (метрики, не рендер; не аллокация).
Нетекстовые размеры — из токенов. **Логика-тесты не зависят от текста**: bounds задаются напрямую через `layout(...)`
(как в `LinearTest`), визуальная корректность `measure`/текста проверяется в dev-галерее (§6).

### 2.7 Token-доступ (фактические аксессоры Foundation)
`Tokens.surface().surface()`, `.bg0()`…; `Tokens.accent().accent()/.accentHi()/.gradientA()/.gradientB()/.onAccent()`;
`Tokens.border().subtle()/.defaultColor()/.strong()/.thickness()`; `Tokens.radius().xs()…xl()`;
`Tokens.spacing().xs()…xxl()`; `Tokens.type().body()/.label()…` → `Role.weight()/.size()/.lineHeight()`;
`Tokens.elevation().level1()/.level2()` → `Level.surface()/.border()/.shadow()/.glow()`;
`Tokens.glow().active()` → `Preset.size()/.color()`;
`Tokens.interaction()` → `hoverWash()/pressOverlay()/disabledAlpha()/focusRing()/focusRingWidth()` (state-визуал, §2.4);
`Tokens.motion()...`; цвет-хелперы `Color.withAlpha/lerp/scaleAlpha`.

### 2.8 Focus & keyboard
**Фокусируемы** только интерактивные виджеты: `Button`, `Toggle`, `Checkbox`, `Slider`. `Label`/`Divider`/`Panel`/
`Card`/`Window`/`ScrollArea` сами не фокусируемы (контейнеры держат фокус через своих интерактивных детей).
Регистрация — у владельца дерева (dev-галерея в M2.2, Stage-3 `Screen` потом) через `FocusManager.register(...)`;
`FocusManager` (frozen §5.3) ведёт Tab-обход, click-to-focus и маршрутизирует `keyPressed`/`charTyped`
сфокусированному. Визуал фокуса — ring `interaction().focusRing()`/`focusRingWidth()` (§2.4).
**Клавиатурная активация** (через `keyPressed` сфокусированного; объём M2.2 — на approve, см. §10.3): `Space`/`Enter`
→ активировать `Button`/`Toggle`/`Checkbox` (эквивалент release-внутри); `Left`/`Right` → `Slider` ±`step`
(малый шаг при `step==0`).

---

## 3. Widget contracts

Формат: назначение · API · поведение/composition · токены/состояния. Точные числовые формулы размеров
(всегда токен-выражения, без литералов) финализируются в реализации и проверяются на token-only при ревью.

### 3.1 `Label` — текст-примитив (leaf)
```java
public final class Label extends Component {
    public Label(String text);                          // роль по умолчанию: type().body()
    public Label(String text, Typography.Role role);
    public Label text(String s);
    public Label role(Typography.Role r);
    public Label align(Align a);                        // LEFT (default) / CENTER / RIGHT
    public Label color(int tokenColor);                 // default — цвет роли (textHi)
    public Label effect(TextEffect e);                  // default NONE
}
```
- **measure:** `w = Ui.text().width(text, role.weight(), role.size())`; `h = Ui.text().lineHeight(role.weight(), role.size())`.
- **render:** один `ctx.text().draw(text, x, y, style)` (поле `TextStyle`, пересобирается лениво только при смене
  text/role/align/color/effect — не каждый кадр). Однострочный в M2.2 (wrap — M2.3).
- **align:** действует только когда назначенная `w` > ширины текста (напр. при `CrossAlign.STRETCH`); иначе Label
  плотно облегает текст (intrinsic-ширина = ширине текста) и align — no-op.
- **states:** нет (не интерактивен). Цвет наследует disabled-alpha родителя через `pushOpacity`, если применяется.

### 3.2 `Divider` — разделитель 1px (leaf)
```java
public final class Divider extends Component {
    public Divider();                 // HORIZONTAL по умолчанию
    public Divider(Axis axis);
    public Divider color(int tokenColor);   // default border().subtle()
}
```
- **measure:** HORIZONTAL → `Size(0, thickness)`; VERTICAL → `Size(thickness, 0)`, где `thickness = border().thickness()`.
  Растягивается по main-оси родителя (Fill) или cross-оси (`CrossAlign.STRETCH`).
- **render:** `ctx.renderer().rect(x, y, w, h, color)` (тонкая полоса по назначенным bounds).

### 3.3 `Panel` — плоская поверхность-группировка (container, 1 child)
```java
public final class Panel extends Container {
    public Panel();
    public Panel(Component child);
    public Panel child(Component c);            // ровно один child (обычно layout-контейнер)
    public Panel padding(Insets p);             // default ZERO; токен-инсеты
}
```
- **Назначение:** сгруппировать содержимое на плоской поверхности (БЕЗ тени — отличие от `Card`).
- **measure:** `child.measure(...)` + `padding`.
- **layout:** child в inner-rect (bounds − padding).
- **render:** `roundedRect(x,y,w,h, radius().md(), surface().surface())` → опц. `border(..., border().subtle())` →
  `pushRoundedClip(...)` → child → `popClip()`.

### 3.4 `Card` — elevated-поверхность с опц. header/footer (container)
```java
public final class Card extends Container {
    public Card();
    public Card(Component content);
    public Card content(Component c);
    public Card header(Component c);    // опц.
    public Card footer(Component c);    // опц.
    public Card padding(Insets p);      // default spacing().md
}
```
- **Composition:** внутренний `Column[header?, content, footer?]` (header/footer разделяются `Divider` при наличии).
- **render:** `Elevation.level1` — `shadow(...)` (из `level1().shadow()`) → `roundedRect(radius().lg(), level1().surface())`
  → `border(..., level1().border())` → rounded-clip → внутренний Column. Без glow (level1.glow == null).
- **measure/layout:** делегируются внутреннему Column + padding.

### 3.5 `Window` — root-контейнер: title-bar + frame + content (container; position-exception)
```java
public final class Window extends Container {
    public Window(String title);
    public Window content(Component c);     // ровно один content-child (opt-in scroll: оборачивается в ScrollArea ВНЕ Window)
    public Window position(float x, float y);   // владелец задаёт экранную позицию (не родительский layout)
    public Window size(float w, float h);       // явный размер окна (frame)
}
```
- **Composition (APPROVED — opt-in scroll):** внутренний `Column[ titleBar, content? ]`. `titleBar` — приватный
  **захватываемый drag-handle** (рисует title `type().title()`, фон `surface().surfaceHi()`, резервирует место под
  будущие кнопки); `content` — один content-child (rounded-clip), занимает остаток. Скролл НЕ встроен — нужен скролл,
  потребитель оборачивает content в `ScrollArea` сам (намеренное уточнение эскиза frozen §7.1 «chrome + ScrollArea»).
- **Position-exception (frozen §1.3):** `Window` держит свои `x,y`; экранную позицию задаёт владелец
  (`position(...)`/перетаскивание), не родительский layout. На верхнем уровне (root) — единственный, кому это разрешено.
- **Drag — полностью по ссылке (никакого hit-test/флага в `Window`):** `titleBar.mouseClicked` возвращает `true`
  (поглощает press) → родитель захватывает его как `pressedChild`; `titleBar.mouseDragged(...)` вызывает
  `window.moveBy(dx,dy)`. `Window` **не переопределяет** диспатч ввода — capture естественно ведёт жест к ручке, а
  press по content-child уходит к контенту. Появятся интерактивные дети в баре (close-кнопка, M2.3) — z-порядок
  отдаёт им приоритет (сверху-вниз), пустая зона бара попадает в ручку. Единообразно с §F.1 «routing by reference,
  NOT hit-test»; нет `dragging`-флага и `titleBarContains` → исключены залипший флаг и двойной механизм захвата.
- **`moveBy(dx,dy)`:** сдвигает `Window.x,y` и переинвокирует собственный `layout(x,y,w,h)`, чтобы дети следовали
  (position-exception §1.3).
- **render:** `Elevation.level2` — `shadow(level2().shadow())` → `roundedRect(radius().lg(), level2().surface())` →
  `border(level2().border())` → `titleBar` → rounded-clip `content`. Без content — рисуются только рамка+бар (§2.1).

### 3.6 `ScrollArea` — прокрутка + клип контента (container, 1 child)
```java
public final class ScrollArea extends Container {
    public ScrollArea(Component content);
}
```
- **Поведение:** вертикальный скролл колесом + перетаскивание thumb-а скроллбара (pointer-capture). `offset` (px)
  клампится в `[0, max(0, contentH − viewportH)]`. Скроллбар (тонкий track + thumb справа) виден только при
  переполнении.
- **mouseScrolled:** **R13 решён на уровне базового `Container`** (committed `23ff2c3`): disabled-компонент не
  получает `mouseScrolled` от родителя — единообразно с click/capture; `ScrollArea` отдельного `!enabled`-гарда не
  требует. При `enabled` — сдвигает `offset`, возвращает true если сдвиг произошёл.
- **layout:** content измеряется на полную высоту, кладётся в `(x, y − offset, viewportW', contentH)`
  (viewportW' = ширина минус скроллбар при наличии).
- **render:** `pushClip(viewport)` → content → `popClip()` → скроллбар (`roundedRect` thumb акцентом subtle,
  track `border().subtle()`). Thumb-высота ∝ `viewportH/contentH`; его drag (`mouseDragged.dy`) сдвигает `offset`
  пропорционально (capture через `mouseClicked/mouseDragged/mouseReleased`). Клик-пейджинг по треку — вне M2.2
  (колесо + thumb-drag).
- **Future-proof (frozen §11):** offset/measure разделены так, что позже можно рисовать только видимые дети; сейчас
  рисуются все.

### 3.7 `Button` — действие (interactive; composes Label)
```java
public final class Button extends Component {
    public enum Variant { PRIMARY, GHOST }              // APPROVED — только два варианта
    public Button(String label);
    public Button variant(Variant v);                   // default PRIMARY
    public Button onClick(Runnable r);
}
```
- **Composition:** держит внутренний `Label` (центр). measure = label-size + `padding` (`symmetric(spacing().md, spacing().sm)`).
- **Commit-модель (pointer-capture):** press внутри → `pressed=true`, capture (`pressedChild`); `release` **внутри**
  bounds → `onClick.run()`; release вне → отмена (cancel). hover/press-визуал — через `Transition` (§2.5).
- **Variant PRIMARY:** заливка `accent().accent()`; текст — `accent().onAccent()` (G4-токен: контрастный передний
  план на accent-заливке; в ClubDark = `ink0` — контраст держит тема, не виджет). hover — осветление
  (`Color.lerp(accent().accent(), accent().accentHi(), t)`), press — overlay `interaction().pressOverlay()`.
- **Variant GHOST:** прозрачный фон + `border(border().defaultColor())`; текст `palette().textHi()`; hover — overlay
  `interaction().hoverWash()` + accent-текст (`Color.lerp(palette().textHi(), accent().accent(), t)`).
- **focus:** ring `border(x,y,w,h, radius().sm(), interaction().focusRingWidth(), interaction().focusRing())`.
  **disabled:** `renderer().pushOpacity(interaction().disabledAlpha())`, ввод игнорируется (база не диспатчит на `!enabled`).
- **radius:** `radius().sm()`. Все state-визуалы — только из `Tokens.interaction()`/`accent()` (без литералов).

### 3.8 `Toggle` — вкл/выкл (leaf, bare)
```java
public final class Toggle extends Component {
    public Toggle(boolean value);
    public Toggle onChange(BoolConsumer cb);
    public boolean value();
}
```
- **Bare-контрол** (APPROVED): без встроенного лейбла; подпись компонуется снаружи (`Row[Label, Toggle]`).
- **Размер:** из токенов (track height ≈ `type().body().lineHeight()`, width = height + knob travel из `spacing()`).
- **render:** OFF — track `surface().surfaceHi()`/`border`; ON — **accent-градиент** (`gradient(... gradientA(), gradientB(), HORIZONTAL)`)
  + сдержанный `glow(... glow().active())` (единственное санкц. применение glow, frozen §3.3). Knob —
  `circle(..., palette().white())`, позиция анимируется `Transition` (`durations().normal()`, `easings().standard()`).
- **commit:** release-внутри → `value = !value; cb.accept(value)`.
- **states:** focus-ring `interaction().focusRing()`/`focusRingWidth()`; disabled `pushOpacity(interaction().disabledAlpha())`.

### 3.9 `Checkbox` — булев флаг (leaf, bare)
```java
public final class Checkbox extends Component {
    public Checkbox(boolean value);
    public Checkbox onChange(BoolConsumer cb);
    public boolean value();
}
```
- **Bare-контрол:** подпись — снаружи (`Row[Checkbox, Label]`).
- **Размер:** квадрат ≈ `type().body().lineHeight()`, `radius().xs()`, `border().thickness()`.
- **render:** OFF — `roundedRect(surface().surfaceHi())` + `border(border().defaultColor())`; ON —
  `roundedRect(accent().accent())` + галочка двумя `line(...)`; check-in анимируется `Transition` (α/scale).
- **commit:** release-внутри → toggle + `cb`.
- **states:** focus-ring `interaction().focusRing()`/`focusRingWidth()`; disabled `pushOpacity(interaction().disabledAlpha())`.

### 3.10 `Slider` — число (composite: Row[track, value Label])
```java
public final class Slider extends Container {
    public Slider(float value, float min, float max, float step);   // step==0 → непрерывный
    public Slider onChange(FloatConsumer cb);
    public Slider showValue(boolean show);     // default true (APPROVED)
    public float value();
}
```
- **Composition (APPROVED):** внутренний `Row[ track (Fill), valueLabel (Fixed) ]`; `track` — приватный
  interactive-компонент (рисует track + flat accent-fill + knob, ведёт drag), `valueLabel` — `Label`
  (`type().label()`). `showValue(false)` убирает label из Row.
- **track render:** базовый трек (толщина = `2 × border().thickness()`) `surface().surfaceHi()`; залитая часть
  `accent().accent()` (ПЛОСКАЯ, без градиента — frozen DESIGN); knob — `circle(...)` на позиции value.
- **measure:** делегируется внутреннему `Row`; `track` имеет минимальную intrinsic-ширину из токенов
  (напр. кратную `spacing().xxl()`), чтобы slider получал разумную ширину, когда родитель не назначает ему `Fill`.
- **drag (pointer-capture):** press на track/knob → capture; `mouseDragged` мапит `mouseX → value` по ширине трека,
  кламп в `[min,max]`, снап к `step` (если `step>0`); `cb.accept(value)`; release — конец drag.
- **value-format (APPROVED авто):** целое если `step` целочисленный (или диапазон целочисленный), иначе 1 знак после
  запятой (`step==0` → дробный режим). Разделитель — `.` (locale-independent). valueLabel обновляет текст при смене value.
- **states:** focus-ring `interaction().focusRing()`/`focusRingWidth()` на track; disabled
  `pushOpacity(interaction().disabledAlpha())`. Без литералов состояний.

---

## 4. Resolved decisions (сводка, APPROVED 2026-06-28)

| # | Решение |
|---|---|
| Pointer capture | `pressedChild`-цепочка по `Container` (§F, committed `23ff2c3`); `mouseDragged` с дельтами; release на `mouseReleased` (без авто-free на hide/disable/detach — см. §10); без глобального менеджера. |
| Button variants | Только `PRIMARY` + `GHOST`. DANGER/SECONDARY — при реальной нужде в M2.3, без слома контракта. |
| Slider | `float`; value-label показан по умолчанию, `showValue(false)` скрывает; формат авто. |
| Window scroll | Opt-in: `Window` без встроенного `ScrollArea`; потребитель оборачивает content сам. |
| R13 disabled-scroll | Базовый `Container.mouseScrolled` уважает `enabled` (committed `23ff2c3`) — disabled-компонент (вкл. `ScrollArea`) не получает scroll, единообразно с click/capture. Виджет отдельного гарда не требует. |
| Interaction tokens | `Tokens.interaction()` (hoverWash/pressOverlay/disabledAlpha/focusRing/focusRingWidth, committed `23ff2c3`) — единственный источник state-визуала; в виджетах никаких литеральных α/цветов. |
| Button onAccent (G4) | `Accent.onAccent` добавлен в токены (=ink0) — PRIMARY-текст = `accent().onAccent()`; контраст держит тема. Аддитивно, frozen-спека §3.3/§10. |
| Keyboard | Widget-scope: `Space`/`Enter` → Button/Toggle/Checkbox; `Left`/`Right` → Slider ±step (§2.8). Включено в M2.2. |
| Capture auto-release | **НЕ** в M2.2: освобождение только на `mouseReleased`; free-on-invalidate + force-release-hook — Stage-3 improvement (§10.2). |
| Toggle/Checkbox label | Bare-контролы; подпись компонуется снаружи через `Row`. |
| Icons | Icon-системы нет → виджеты текстовые; иконки — отдельная задача позже (эскалация). |
| Callbacks | `Runnable` (Button), `BoolConsumer`, `FloatConsumer` в `component/widget`. |

---

## 5. Dev-галерея (gated, удаляется перед merge — frozen §8.1)

Каталог-экран всех 10 виджетов во всех состояниях (resting/hover/press/focus/disabled, ON/OFF, варианты Button,
Slider с/без value). **Инструмент разработки, не часть архитектуры клиента:** gated, вне `main`-сборки, удаляется
перед merge. Здесь — единственный вызов `Ui.init()` (регистрация шейдеров/атласа) на время проверки и подача
`UiContextImpl.setTime(...)` + форвардинг ввода (включая `mouseDragged/mouseReleased` для capture). Приёмка —
скриншоты 1× и на зуме: текст резкий, скругления/бордеры/градиент/glow без banding/ступеней/CPU-AA.

---

## 6. Testing (чистые headless + визуальная приёмка)

**Headless (логика, без GL — главная тестовая поверхность):**
- **Pointer capture (§F) — тесты уже есть и зелёные** (`CaptureTest`, committed `23ff2c3`): press→drag→release до
  листа (вкл. вложенные контейнеры), off-bounds-доставка, capture очищается на release, disabled/declined-press не
  захватываются. M2.2 их **не дублирует**; вложенный drag `Window` (own-drag поверх captured content-child) —
  покрывается тестом `Window`.
- **Виджет-логика:** Button onClick только при release-внутри (cancel при release-вне); Toggle/Checkbox
  переключение + `onChange`; Slider value-кламп/снап-к-step/мапинг drag→value + `onChange`; ScrollArea offset-кламп
  и R13 (disabled не скроллит).
- **Клавиатура (widget-scope, §2.8):** `Space`/`Enter` активирует сфокусированный Button/Toggle/Checkbox (как
  release-внутри); `Left`/`Right` меняет Slider на ±`step`; disabled/`!focused` — игнор.
- **State-машина:** hovered/pressed/focused/disabled-переходы; диспатч ввода контейнером; focus-обход.
- bounds в тестах задаются напрямую `layout(...)` (без зависимости от текста/GL).
- **Arch-guard:** `ArchitectureRuleTest` зелёный (виджеты не используют low-level вне backend).

**Визуальная приёмка:** dev-галерея, 1× и зум (§5).

---

## 7. Acceptance criteria (M2.2)

- §F (capture + `Interaction`-токены + R13) **уже реализован и смержен** (infra-prep `23ff2c3`, 76 тестов зелёные);
  M2.2 строится на нём и **его не меняет**. Ни одна сигнатура Stage-1/M2.1 не сломана (изменения §F — аддитивный
  `Component.mouseDragged` + внутренности `Container` + новый `Interaction`-токен).
- Все 10 виджетов рисуют только через `ctx`; **никаких литералов** (только токены, вкл. `interaction()` для
  состояний); position-passive соблюдён
  (кроме `Window`); композиция через существующие контейнеры.
- Логика покрыта headless-тестами; dev-галерея демонстрирует все состояния резко на 1× и зуме (нет
  banding/ступеней/CPU-AA).
- Ревью (adversarial на финале milestone) без Critical/Important. Dev-галерея/`Ui.init()`-хук удалены перед merge.

---

## 8. Charter V2 compliance

- **backend-only** ✓ — рендер только через `ctx.renderer()/text()`; метрики через `Ui.text()`.
- **нет low-level вне backend** ✓ — гарантирует `ArchitectureRuleTest`.
- **контракты Stage 1 (рендер) не менялись** ✓ — `UiRenderer/UiText/Ui/UiContext` нетронуты. Расширение базы —
  аддитивный pointer-capture (`Component.mouseDragged` + `Container`-внутренности) + `Interaction`-токен + R13
  (**committed `23ff2c3`**) + аддитивный `Accent.onAccent` (G4, утв. при approve M2.2-спеки, §F). M2.2-виджеты базу
  не меняют.
- **gui/hud не тронуты** ✓.

---

## 9. File map (предварительно — финализируется в плане M2.2)

**Foundation baseline (§F) — `23ff2c3` + G4, M2.2-виджеты НЕ трогают:**
- (committed `23ff2c3`) `component/Component.java` (+`mouseDragged`) · `component/Container.java`
  (`pressedChild`-capture + `mouseDragged`/`mouseReleased`, R13 в `mouseScrolled`) · `theme/Interaction.java`
  (+ `Theme`/`Tokens`/`ClubDark`).
- (G4, аддитивно — коммитится как infra перед виджетами) `theme/Accent.java` (+`onAccent`) · `theme/themes/ClubDark.java`
  (`onAccent = ink0`) · `theme/TokensTest` (onAccent↔palette).
- Тесты: `component/CaptureTest.java`, `ContainerTest` (R13), `TokensTest` (Interaction + onAccent) — зелёные.

**Новое — `com.club.ui.component.widget` (это и есть работа M2.2):**
- `BoolConsumer.java`, `FloatConsumer.java`
- `Label.java`, `Divider.java`, `Panel.java`, `Card.java`, `Window.java`, `ScrollArea.java`,
  `Button.java`, `Toggle.java`, `Checkbox.java`, `Slider.java`

**Тесты (новые, M2.2):** по тесту логики на интерактивные виджеты (`widget/*Test.java`); `Window` own-drag поверх
captured content-child. Capture-механику не перетестируем (покрыта `CaptureTest`).

**Dev-галерея (gated, удаляется перед merge):** отдельный gated-каталог (как acceptance-screen Stage 1).

---

## 10. Resolved decisions (approve 2026-06-28) — открытых вопросов нет

Все три прежних развилки закрыты вашим approve:

1. **Button PRIMARY — цвет текста на accent-заливке → RESOLVED (10.1).** Введён явный токен **`Accent.onAccent`**
   (G4, §3.3/§3.8 frozen-спеки; в ClubDark = `ink0`). Button PRIMARY-текст = `accent().onAccent()`. Контраст теперь
   держит тема (а не хрупкий `surface().bg0()`), при тёмной accent-теме правится значением `onAccent` без слома API.

2. **Capture auto-release при `!visible`/`!enabled`/detach + проглоченный release → DEFERRED to Stage 3 (10.2).**
   В M2.2 capture освобождается **только на `mouseReleased`** (текущая `pressedChild`-модель). Free-on-invalidate и
   force-release-hook (root-`Screen.removed()`/потеря фокуса) — **Stage-3 improvement**, не входят в M2.2. В M2.2
   единственный root — gated-галерея, риск «висящего» захвата пренебрежим.

3. **Объём клавиатурной активации → RESOLVED, widget-scope (10.3).** Включается в M2.2 через `keyPressed`
   сфокусированного: `Space`/`Enter` → активировать Button/Toggle/Checkbox; `Left`/`Right` → Slider ±`step` (§2.8).
   Не Foundation — это виджет-логика; покрывается тестами (§6).

Публичный Widget API §3 **заморожен**. Прочие вопросы по ходу реализации эскалируются до написания затрагиваемого кода.
