# Item Scroll — спека модуля

> Источник задачи: `docs/TZ-A-ITEMSCROLL.md`. Границы: `docs/NEXT-PLAN.md` §3–4. Разведка: `docs/research/itemscroll.md`.
> Этот файл — что мы **строим** (и чем это отличается от ТЗ после решений владельца), и он же остаётся
> документацией модуля.

---

## 0. Решения владельца, принятые поверх ТЗ (2026-07-13)

| Тема | ТЗ | Решение | Следствие |
|---|---|---|---|
| Темп кликов | настройка `Speed: Instant / Paced (15/s)`, токен-бакет, атомарные группы, индикатор прогресса | **Выкинуто целиком.** Модуль мгновенный — как оригинал. | Нет `ClickPacer`, нет очереди, нет прогресс-бара поверх ванильного экрана, нет харнесс-проверки «≤3 клика в тик». Батч исполняется inline в обработчике события. |
| Матрица жестов | ряды в поповере (`GestureRow`) | **Отдельный экран** `Edit Gestures…` | Поповер — фикс 236px под сеткой карточек; 7 рядов с чипами и подписями конфликтов туда не влезают, а новый тип ряда потребовал бы правок в замороженном `com/club/ui/menu/**`. Прецедент: HUD Editor (`ActionSetting` → `Screen`). |
| Скролл в модели жеста | `SCROLL_UP` / `SCROLL_DOWN` как отдельные входы | **Один вход `SCROLL`**, направление — аргумент действия | Иначе дефолт «scroll = move one» распадается на две привязки, а правило «вверх = наружу, вниз = внутрь» умирает. Инверсия — галкой `Reverse scroll`. |
| Чужие моды ниши | не оговорено | **Стоим в стороне** | Item Scroller / Mouse Tweaks / Inventory Profiles Next / Mouse Wheelie: `isModLoaded` → не вмешиваемся, `notice()` пишет почему. Два мода на один жест = двойной перенос и десинк, который спишут на Club. |
| Строки в общих файлах меню | одна под `[SEAM:cards]` | **Три**: card + `case` в `notice()` + константа `IconGlyph` (0xE01C) | Чату B — свои три (0xE01D). Атлас `icons.png/json` — генерируемый артефакт: кто ребейзится вторым, перегоняет `./gradlew genIconAtlas`, а не мержит бинарь руками. |

Единственное, что остаётся от темы темпа: **поштучного фолбэка нет.** Если `QUICK_MOVE` не может положить стак
(приёмник полон) — мы просто ничего не двигаем и останавливаемся. Оригинал в этом месте начинает перекладывать
предметы по одному и уходит в ~1000 пакетов (их issue #70); мы туда не идём.

---

## 1. Действия и композиция кликов

Единственный шов наружу — `HandledScreen.onMouseClick(slot, slotId, button, SlotActionType)` (через `@Invoker`;
он сам зовёт `interactionManager.clickSlot`, а модовые экраны, которые его переопределяют, сохраняют свою
семантику). Один вызов = один `ClickSlotC2SPacket`. Батч-пакета не существует.

| Действие | Клики |
|---|---|
| `MOVE_STACK` | `QUICK_MOVE(slot, 0)` |
| `MOVE_MATCHING` | `QUICK_MOVE(s, 0)` по каждому слоту региона, чей стак совпадает с наведённым |
| `MOVE_EVERYTHING` | `QUICK_MOVE(s, 0)` по каждому непустому слоту региона |
| `MOVE_ONE` | `PICKUP(src,0)` → `PICKUP(dst,1)` → `PICKUP(src,0)`; при `count == 1` — короткий путь `QUICK_MOVE(src,0)` |
| `DROP_ONE` / `DROP_STACK` | `THROW(slot, 0)` / `THROW(slot, 1)` |
| `DRAG_MOVE` | `QUICK_MOVE(s, 0)` по каждому задетому слоту, дедуп по id |

Ограничения (проверены по байткоду 1.21.1): `PICKUP`/`QUICK_MOVE` принимают только кнопки 0 и 1 — средняя
кнопка мыши не может быть slot action, она только вход. `QUICK_MOVE` по слоту результата крафта повторяет крафт,
пока есть ингредиенты — поэтому слот результата обслуживается **только** `QUICK_MOVE` (композиция из `PICKUP`
скрафтила бы дважды). `CLONE` — креатив. `SWAP`, `PICKUP_ALL`, `QUICK_CRAFT` не используем; при совпадении
нашего drag-жеста ванильный quick-craft отменяем (возврат `false` из `allowMouseClick`).

**Курсор.** `MOVE_ONE` и оба `DROP_*` требуют пустого курсора до и после. Правило шире и проще: **если на
курсоре что-то лежит, модуль не вмешивается вообще** — событие уходит ванили. Иначе пришлось бы объяснять
игроку, почему один жест работает с занятым курсором, а другой молча ничего не делает.

## 2. Регионы слотов

Без арифметики по индексам (она ломается на модовых экранах): слот «на стороне игрока» ⟺
`slot.inventory instanceof PlayerInventory`, всё остальное — контейнер. Исключение — `PlayerScreenHandler`
(инвентарь выживания), где обе стороны PlayerInventory: делим по `slot.getIndex()` — хотбар 0–8, основной 9–35
(это и есть два региона); броня 36–39 и офф-хенд 40 **в массовых переносах не участвуют**; сетка крафта 1–4 и
результат 0 — «контейнерная» сторона.

Пропускаем `!slot.isEnabled()` (ткацкий станок, торговец). Никогда не целимся в слот с `!slot.canInsert(stack)`.

**Направление.** Скролл: вверх = наружу из инвентаря наведённого слота, вниз = внутрь (галка `Reverse scroll`
инвертирует). Клик: направления нет — всегда «наружу из инвентаря наведённого слота».

## 3. Модель жестов

```java
enum ScrollAction { MOVE_ONE, MOVE_STACK, MOVE_MATCHING, MOVE_EVERYTHING, DROP_ONE, DROP_STACK, DRAG_MOVE }
enum GestureInput { SCROLL, LMB, RMB, MMB }
record Gesture(int mods, GestureInput input) {}     // mods: SHIFT=1 | CTRL=2 | ALT=4
```

Конфиг: `Map<ScrollAction, Gesture>` — ровно один жест на действие.

**Домены входов.** `DRAG_MOVE` требует зажатой кнопки → только LMB/RMB/MMB. Остальным доступны все четыре входа.

**Дефолты** (мышечная память оригинала):

| Действие | Жест |
|---|---|
| Move one | `SCROLL` |
| Move stack | `Shift + SCROLL` |
| Move matching | `Ctrl + SCROLL` |
| Move everything | `Ctrl + Shift + SCROLL` |
| Drag move | `Shift + LMB` |
| Drop one / Drop stack | **не назначены** — промах рассыпает стак на пол сервера |

**Двусмысленность невозможна, тремя слоями** (грамматика Stage 62, буква в букву):

1. **Отбор.** `ItemScrollBinds.set(action, gesture)` сначала снимает этот жест со всех остальных действий
   (идиома `removeIf` из `ModuleBinds.set`). Потерявший ряд уходит в «Not set», назначающий на один такт
   показывает «Taken from Move Stack».
2. **Зарезервированное — отказ.** Голые `LMB` и `RMB` без модификаторов — это то, чем берут предметы; перекрыть
   их значит сломать инвентарь. Отказываем голосом `bindReserved`: *«That's how you pick items up»*.
3. **Ваниль — называем.** `GestureConflicts.vanilla(gesture)` — аналог `KeyConflicts.other()`: `Shift+LMB` →
   *«Quick move»*, `Shift+RMB` → *«Quick move»*, `MMB` → *«Clone stack (creative)»*. Назначать **разрешаем**,
   но ряд несёт янтарную подпись *«Overrides: Quick move»* (`Tokens.palette().stateWarn()`).

**Персистенция** — форма `moduleBinds`, уже проверенная: `Map<String,String>` вида
`"MOVE_MATCHING" -> "CTRL+SCROLL"`, `"DROP_ONE" -> ""` (пусто = явно снят, в отличие от отсутствующего ключа =
дефолт). Разбор через `BAD`-множество, как `ModuleBinds.key()`: правка файла руками не должна ронять клиент —
кривое значение отбрасывается один раз и самозалечивается.

## 4. Поверхность миксинов: ноль `@Inject`

```java
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("focusedSlot")     Slot club$focusedSlot();
    @Accessor("x")               int  club$x();
    @Accessor("y")               int  club$y();
    @Accessor("backgroundWidth") int  club$bgW();
    @Accessor("backgroundHeight")int  club$bgH();
    @Invoker("getSlotAt")        Slot club$slotAt(double x, double y);   // private в 1.21.1
    @Invoker("onMouseClick")     void club$onMouseClick(Slot s, int id, int button, SlotActionType t);
}
```

`@Accessor`/`@Invoker` генерируют методы, не переписывают тела и не претендуют на точку инжекта — Sodium, REI,
EMI, IPN могут миксинить тот же класс, и мы не подерёмся. У `HandledScreen` **нет** метода `mouseScrolled`
(наследует дефолт `ParentElement`) — инжектить некуда, и не нужно: всё ловится Fabric-событиями.

```java
ScreenEvents.AFTER_INIT → если screen instanceof HandledScreen:
    ScreenMouseEvents.allowMouseScroll   → жест-скролл   (false = мы съели событие)
    ScreenMouseEvents.allowMouseClick    → жест-клик, старт drag
    ScreenMouseEvents.allowMouseRelease  → конец drag
    ScreenEvents.afterRender             → сэмпл слота под курсором во время drag
    ScreenEvents.remove                  → сброс состояния
```

Drag-события в Fabric API нет, и не надо: drag = клик (взвод) → сэмплирование `club$slotAt` каждый кадр →
release (конец). Это лучше миксина в `mouseDragged` — ванильный `QUICK_CRAFT` вообще не стартует.

**Три правила совместимости:**
1. Действуем **только** когда есть слот под курсором **и** курсор внутри `[x, x+bgW] × [y, y+bgH]`. Оверлеи и
   поиск REI/EMI/JEI живут снаружи этой коробки — мы им скролл не воруем.
2. `screen instanceof CreativeInventoryScreen` → не вмешиваемся (фейковые слоты, переопределённый
   `onMouseClick`). На карточке об этом сказано вслух.
3. Мод из списка-побратимов загружен → модуль стоит, `notice()` объясняет.

Всё, чего мы не съели, уходит ванили нетронутым (`return true`). Проверяется `-PclubCompat`.

## 5. Что режем в v1 (и говорим об этом на карточке)

Торговля с жителями, память рецептов крафта, креативный инвентарь. `MenuContent.notice("Item Scroll")` →
`«Creative inventory: not handled»`.

## 6. Планировщик — чистый, потому что его надо тестировать

`SlotPlan` не знает про Minecraft. Он работает над снимком:

```java
record SlotView(int id, boolean playerSide, boolean hotbar, boolean enabled,
                boolean empty, int count, Object itemKey) {}
```

Снимок снимается с живого `ScreenHandler` на месте вызова, планировщик возвращает `List<Click>`
(`slotId, button, SlotActionType`), исполнитель шлёт их через `@Invoker`. Так каждое действие, каждый регион и
каждая граница (броня, офф-хенд, выключенные слоты, слот результата) покрываются юнит-тестом без бутстрапа MC.

## 7. Границы

**Владею:** `com/club/modules/itemscroll/**`, `com/club/mixin/HandledScreenAccessor.java`, `docs/ITEMSCROLL.md`,
`tools/icons/src/E01C_item_scroll.svg`.
**Общие — только швы:** `ClubConfig` (`[SEAM:config]`, одна строка), `MenuContent` (`[SEAM:cards]` + один `case`
в `notice()` + `IconGlyph.ITEM_SCROLL`), `ClubClient` (`[SEAM:init]`), `club.mixins.json` (в конец),
`ClubHarness` (`[SEAM:checks]`).
**`ClubConfig.version` НЕ бумпаю:** новая секция подхватывается Gson-дефолтами без `migrate()`, а два чата,
оба меняющие `9 → 10`, — гарантированный конфликт и порванная цепочка миграций.

## 8. Этапы

| # | Этап | Готово, когда |
|---|---|---|
| 1 | Каркас: конфиг-секция, `ItemScrollModule`, карточка + иконка + notice, `HandledScreenAccessor` | собирается, карточка в Misc есть, модуль ничего не делает |
| 2 | `SlotView` / `SlotPlan` + юнит-тесты | все 7 действий и все границы регионов покрыты, тесты зелёные |
| 3 | Хуки и исполнение: скролл и клик-жесты | в игре: сундук, `MOVE_ONE`/`STACK`/`MATCHING`/`EVERYTHING` работают, курсор пуст |
| 4 | `DRAG_MOVE` | провёл по слотам — перенеслись; ванильный quick-craft не стартует |
| 5 | Модель жестов: `ItemScrollBinds`, `GestureConflicts` + юнит-тесты | отбор, отказ, именование ванили — под тестом |
| 6 | Экран `Edit Gestures…` | 7 рядов, захват жеста, Esc/Delete, янтарные подписи; скриншот владельцу |
| 7 | Харнесс + доки + `-PclubCompat` | проверки под `[SEAM:checks]` зелёные, Sodium/Freecam не сломаны |

## 9. Приёмка

- Сундук 54 слота переезжает целиком, **курсор пуст**, ничего не потеряно.
- `MOVE_ONE` из стака 64: в источнике 63, в приёмнике 1, курсор пуст.
- `MOVE_MATCHING` не трогает предметы других типов.
- Броня и офф-хенд не участвуют в `MOVE_EVERYTHING`.
- Два действия невозможно посадить на один жест (проверка отбора).
- Голый ЛКМ/ПКМ назначить нельзя.
- Скроллу вне коробки контейнера (оверлей REI) мы не мешаем.
