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
| Строки в общих файлах меню | одна под `[SEAM:cards]` | **Одна** в `MenuContent` + константа `IconGlyph` (0xE01C) | Чат B успел сделать шов `ModuleNotices` (2535f68): модуль регистрирует свою честную строку сам, из своего пакета, и `MenuContent.notice()` больше никто не правит. Атлас `icons.png/json` — генерируемый артефакт: кто ребейзится вторым, перегоняет `./gradlew genIconAtlas`, а не мержит бинарь руками. |

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
курсоре что-то лежит, модуль не действует и не съедает событие** — оно целиком уходит ванили. Второе так же
важно, как первое: ванильное «зажать и размазать стак по слотам» живёт на тех же кнопках, и съесть событие
значило бы сломать то, что игрок делает с 1.5, ничего не сделав взамен.

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
public interface MixinHandledScreenAccessor {
    @Accessor("focusedSlot")     Slot club$focusedSlot();
    @Accessor("x")               int  club$x();
    @Accessor("y")               int  club$y();
    @Accessor("backgroundWidth") int  club$backgroundWidth();
    @Accessor("backgroundHeight")int  club$backgroundHeight();
    @Invoker("getSlotAt")        Slot club$slotAt(double x, double y);   // private в 1.21.1
    @Invoker("onMouseClick")     void club$onMouseClick(Slot s, int id, int button, SlotActionType t);

    // Состояние ванильного quick-craft — только на чтение, и ровно для одного: харнесс требует, чтобы
    // после нашего драга оно было пустым. «Вроде сработало» и «размазал стак веером по сетке крафта»
    // отличаются одним неотменённым событием, и это не то, что доказывают комментарием.
    @Accessor("cursorDragging")   boolean club$cursorDragging();
    @Accessor("cursorDragSlots")  Set<Slot> club$cursorDragSlots();
    @Accessor("quickMovingStack") ItemStack club$quickMovingStack();
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
   поиск REI/EMI/JEI живут снаружи этой коробки — мы им скролл не воруем (проверено в харнессе).
2. **Креатив отказан в двух местах, и это не дублирование.** На двери (`AFTER_INIT` не подписывается на
   события `CreativeInventoryScreen`) и в самом `act()`. Один барьер, который однажды обойдут, — это ноль
   барьеров: харнесс сам зашёл на этот экран (ваниль подменяет `InventoryScreen` на креативный) и **унёс 64
   ступеньки на курсоре**, потому что защита стояла только на двери.
3. Мод из списка-побратимов загружен → модуль стоит, `notice()` объясняет.

Всё, чего мы не съели, уходит ванили нетронутым (`return true`). Проверяется `-PclubCompat` (Sodium + Freecam:
82/82, конфликтов миксинов нет).

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

## 8. Этапы — все закрыты

| # | Этап | Коммит |
|---|---|---|
| 1 | Каркас: конфиг, карточка + иконка (0xE01C), `MixinHandledScreenAccessor` | `8d2d140` |
| 2 | `SlotView` / `SlotPlan` + 20 юнит-тестов (TDD) | `b71d370` |
| 3 | Модель жестов: `Gesture`/`Gestures`/`ItemScrollBinds` + 13 юнит-тестов (TDD) | `a7c3c84` |
| 4 | Хуки `ScreenEvents`, исполнение, проверка в игре | `c774c4e` |
| 5 | `DRAG_MOVE` + доказательство против ванильного quick-craft | `a96f10d` |
| 6 | Экран `GestureScreen` («Edit gestures…») | `88ffc60` |
| 7 | Доки + `-PclubCompat` | этот |

## 9. Что доказано в игре (не «должно работать»)

Харнесс на слитом main: **94 passed, 0 failed**. Под Sodium + Freecam — **92 passed, 0 failed**; с Iris и
шейдерпаком поверх — тоже **92 / 0** (две проверки закономерно пропускаются: часть рендера принадлежит
Sodium). «82» в ранней редакции — число с ветки `feat/itemscroll`, где проверок было меньше.

| Проверка | Почему она есть |
|---|---|
| Сундук: `MOVE_EVERYTHING` → пусто, всё у игрока, курсор пуст | приёмка владельца |
| `MOVE_ONE` из 64 → 63 в источнике, ровно 1 у игрока, **курсор пуст** | третий клик не опционален: без него закрытие экрана роняет стак на землю |
| `MOVE_MATCHING` берёт оба стака камня и не трогает землю | смысл действия |
| Выживанческий экран: main → хотбар, **шлем и щит на месте** | единственное место, где оба региона — инвентарь игрока, и кривое правило раздевает |
| Креатив: на курсор ничего не падает | пойман реальный баг (см. §4.2) |
| Драг по трём слотам: press **consumed**, release **consumed**, ванильный quick-craft **не армирован** | «сработало» и «размазал стак по сетке крафта» отличаются одним событием |
| Чужая кнопка и скролл **вне коробки** уходят ванили | так живут REI/EMI |
| Взвод ряда не привязывает его к взводящему клику | классическая ловушка захвата бинда |
| Голый ЛКМ в взведённый ряд — **отказ** («That's how you pick items up») | иначе инвентарь превращается в кирпич |
| Захват жеста **отбирает** его: `Move one` → `Not set`, «Taken from Move one» | два владельца одного жеста невозможны |

## 10. Приёмка (по ТЗ §7)

- [x] Сундук переезжает целиком, курсор пуст, ничего не потеряно.
- [x] `MOVE_ONE` из стака 64: 63 / 1 / курсор пуст.
- [x] `MOVE_MATCHING` не трогает другие типы.
- [x] Броня и офф-хенд не участвуют в `MOVE_EVERYTHING`.
- [x] Два действия невозможно посадить на один жест.
- [x] Голый ЛКМ/ПКМ назначить нельзя.
- [x] Скроллу вне коробки контейнера (оверлей REI) не мешаем.
- [x] Скриншоты экрана жестов — владельцу (`run/screenshots/club-0*-itemscroll-*.png`).
