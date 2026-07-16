# Club — Architecture & Build

> Общая структура проекта, сборка/запуск, конфиг, миксины и приборы. Точка входа для всего остального.
> Если ты здесь первый раз — читай подряд: §2 собрать, §3 найти свой модуль, §6 правило про миксины
> (нарушишь — у чужого игрока не запустится игра), §9 швы, §11 то, из-за чего PR закроют.

## 1. Что это за проект

**Club** — клиентский utility-мод для Minecraft на **Fabric 1.21.1** (Yarn `1.21.1+build.3`, **Java 21**).
Пакет `com.club`, версия `0.1.3` (`gradle.properties`). Меню открывается на **Right Shift**.
«Премиальный» first-person клиент: кастомные анимации/позиционирование рук, screen-stretch, зум/фрилук/
фуллбрайт, стильный HUD на собственном холсте, перенос предметов жестами (Item Scroll) и честная,
измеренная оптимизация (Perf).

**Это не чит-клиент.** Ни killaura, ни ESP, ни reach, ни X-ray — и не будет: PR с ними закрывается
без обсуждения (§11).

Лицензия — **MIT** (`LICENSE`). Шрифт интерфейса — **Onest** под SIL OFL 1.1; файлы называются
`tools/fonts/inter_*.ttf` по историческим причинам (прототипировали на Inter, имя пережило замену), но
внутри Onest. Джар везёт MSDF-атлас — производную шрифта, — поэтому в него кладётся и `OFL_club`
(см. `build.gradle` → `jar {}` и `THIRD-PARTY-NOTICES.md`).

## 2. Сборка и запуск

```powershell
.\gradlew.bat compileJava     # быстрая проверка компиляции
.\gradlew.bat build           # сборка джара + JUnit (41 тест-класс, src/test)
.\gradlew.bat runClient       # запуск дев-клиента (окно MC)
```

Совместимость с соседями по швам — отдельные флаги (зависимости тянутся только с ними, в джар не идут):

```powershell
.\gradlew.bat runClient -PclubCompat            # + Sodium 0.6.13, Freecam 1.3.0
.\gradlew.bat runClient -PclubCompat -PclubIris # …и Iris 1.8.8 (шейдерный конвейер)
```

Генераторы ассетов (не часть `build`, артефакты закоммичены — клон собирается без них):
`genIconAtlas` (SDF-иконки из `tools/icons/src/<HHHH>_<name>.svg`, генератор в `buildSrc`),
`genIconPreviews`, `genPromoIcon`, `genMsdfAtlas` (**только Windows** — качает `msdf-atlas-gen.exe`).

- Дев-окружение: `run/` (в `.gitignore`; мир `saves/Новый мир`, `run/mods` пуст — голый fabric-api).
- Декомпилированный named MC jar (для проверки ванильных внутренностей):
  `~/.gradle/caches/fabric-loom/minecraftMaven/...minecraft-merged-1.21.1...jar` — извлечь класс и `javap -p -c`.
  Здесь это не экзотика, а рабочая практика: половина решений в `modules/perf` и `modules/itemscroll`
  принята по байткоду, а не по документации.

## 3. Карта пакетов

164 java-файла в `src/main/java/com/club`.

```
com.club
├── ClubMod / ClubClient            — инициализация; кейбинд Right Shift → ui.menu.ClubMenuScreen;
│                                     reload-листенер дуотон-иконок (PixelIcons); [SEAM:init]
├── config/ClubConfig               — модель настроек + load/save/migrate (Gson); ArmorLayout
├── harness/                        — ТРИ ПРИБОРА (dev-only, включаются env-переменными)  → §7
│   ├── ClubHarness                 — самоуправляемый клиент: асерты + скриншоты; [SEAM:checks]
│   ├── ClubBench                   — A/B-бенч с чередованием ON/OFF; умеет сказать INVALID
│   └── ClubPromo                   — режиссёр промо-кадров для галереи
├── ui/                             — V2 UI-стек (MSDF/SDF)  → UI-V2-MENU.md
│   ├── backend/                    — MODERN/LEGACY рендер, шейдеры, атласы, IconBatch
│   │                                 (единственное место low-level GL)
│   ├── component/ + widget/        — Component-дерево; Button/Toggle/Checkbox/Slider/Label/ScrollArea
│   ├── layout/ · motion/ · text/ · theme/ — размеры; Transition/Reveal/ValueTween; TextStyle; Tokens (ClubDark)
│   ├── menu/                       — ClubMenuScreen + MenuContent (боевое меню, [SEAM:cards])  → UI-V2-MENU.md
│   ├── hud/                        — элементы HUD + HudCanvas + HudEditorScreen  → HUD-LANGUAGE.md
│   ├── ClubCanvas                  — свой холст: HUD не зависит от GUI Scale (Stage 63)
│   └── IconGlyph                   — SDF-иконки (PUA, 51 глиф; HUD-глифы = фоллбек дуотона)
├── util/                           — Mth (clamp/lerp/smooth/snap), Keys (сырой опрос GLFW), KeyNames
├── hud/                            — HudManager (диспетчер, HudRenderCallback) + PixelIcons (дуотон
│                                     ванильных текстур, DrawContext-шов) + PotionHud/TargetHud (data-only)
│                                     + HudSpace (разовый перенос старых позиций в холст) + PixelMath
├── modules/
│   ├── ModuleNotices               — реестр «почему модуль стоит»: строку регистрирует САМ модуль
│   ├── animations/                 — AnimationType / Pose / AnimationModule  → ANIMATIONS.md
│   ├── hands/HandsModule           — масштаб/смещение рук
│   ├── screenstretch/              — ScreenStretchModule / StretchPreset (дефолт AUTO — см. migrate v8)
│   ├── zoom/ZoomModule             — hold-зум (eased FOV-делитель, колесо, персист на отпускание,
│   │                                 sensitivityScale: обзор замедляется по tan-отношению полу-FOV)
│   ├── fullbright/FullbrightModule — статик-зеркало гамма-оверрайда (READ-side, 15.0), scope = lightmap
│   ├── togglesprint/               — авто-спринт (vanilla-toggle-safe) + HUD-чип SprintElement
│   ├── freelook/FreelookModule     — hold-обзор камеры (перспектива+yaw/pitch, игрок не крутится)
│   ├── binds/                      — ДВА пространства клавиш, воруют ключ друг у друга (одна физ.
│   │   ├── ModuleBinds               клавиша = одно действие): toggle-бинды модулей (name→translationKey,
│   │   │                             фронты в тике) …
│   │   ├── HoldKeys                … и hold-клавиши (Zoom/Freelook) — это НАСТОЯЩИЕ ванильные
│   │   │                             KeyBinding'и; поповер правит их, toggle-бинда у них нет
│   │   └── KeyConflicts            — конфликты с ванилью НАЗЫВАЮТСЯ, а не молчат (Stage 62)
│   ├── itemscroll/  (15 файлов)    — перенос предметов жестами  → ITEMSCROLL.md
│   │   ├── ItemScrollModule/Hooks  — состояние + вся проводка через Fabric ScreenEvents/ScreenMouseEvents
│   │   ├── Gesture/GestureInput/   — грамматика жеста (SCROLL — ОДИН вход, направление = аргумент),
│   │   │   Gestures/ScrollAction     правила «один вход = одно действие», зарезервированные жесты
│   │   ├── ItemScrollBinds/Config  — живая карта жестов над конфигом; GestureScreen — свой экран
│   │   ├── SlotView/SlotSnapshot/  — ЧИСТАЯ часть: слоты уплощаются, план кликов считается без
│   │   │   SlotPlan/Click            Minecraft и проверяется юнит-тестами (SlotPlanTest, GesturesTest)
│   │   └── ItemScrollMenu/Harness  — карточка в меню; собственные проверки для ClubHarness
│   └── perf/  (9 файлов)           — оптимизация  → PERF.md
│       ├── ParticleCull            — частицы вне фрустума не тесселируются (ваниль не режет их ВООБЩЕ)
│       ├── BlockEntityCull         — BE внутри видимой секции, но вне экрана; ванильный namespace И
│       │                             ванильный класс рендерера — оба, иначе стираем чужой мод
│       ├── BackgroundThrottle      — кап FPS в фоне (в игре даёт РОВНО НОЛЬ — это про батарею и кулер)
│       ├── MainFrustum/IrisCompat  — фрустум ГЛАВНОЙ камеры этого кадра; shadow-pass Iris не пускается
│       ├── FrameStats/HudProfiler  — приборы: порядковые статистики, доля кадра, а не миллисекунды
│       ├── DrawBoxes               — тест вместо обещания: судится КАЖДЫЙ кадр, вердикт по худшему
│       └── PerfMenu                — карточка + ModuleNotices («Entity culling: handled by Sodium»)
├── policy/  (2 файла)              — Club СОБЛЮДАЕТ правила сервера (не обход, а обратное)
│   │                                 → superpowers/specs/2026-07-16-server-policy-design.md
│   ├── ServerFeature               — что вообще бывает запрещено (пока только ITEM_SCROLL)
│   └── ServerPolicy                — таблица правил ЗАШИТА (правило в конфиге = обход нашими
│                                     руками); чистое ядро host/lookup — таблица ПАРАМЕТР, оттого
│                                     тестируемо при пустой боевой таблице; + адаптер allows()
└── mixin/  (16 штук)               — §6
```

Ресурсы: `assets/club/ui/font/msdf` (MSDF-атлас текста), `assets/club/ui/icon/msdf/icons.{png,json}`
(SDF-иконки, 51 глиф, генератор в buildSrc), `assets/club/shaders/core/ui_*` (sdf_shape, sdf_batch,
msdf_text, icon), `assets/club/lang/{en_us,ru_ru}.json`, `assets/club/icon.png`, `fabric.mod.json`,
`club.mixins.json`. (TTF-исходники шрифтов — в `tools/fonts/`, в джар не идут; в джар идёт только атлас.)

## 4. Точки входа

- `ClubMod` (main) — общая инициализация.
- `ClubClient` (client) — `Ui.init()` (шейдеры V2 — строго до стартового resource reload),
  `ClubConfig.load()`, зеркало Fullbright, `HudManager.init()`, reload-листенер `PixelIcons`, кейбинды
  `key.club.open_menu` (Right Shift) / `key.club.zoom` (C) / `key.club.freelook` (Left Alt),
  тик-хендлер, `[SEAM:init]` (регистрация модулей), дренаж конфиг-писателя на `CLIENT_STOPPING`,
  и подъём трёх приборов, если взведена их env-переменная.
- Клавиша меню **опрашивается сырым GLFW** (`util.Keys`), а не через `KeyBinding.wasPressed()`:
  ваниль раздаёт одну физическую клавишу ровно одному биндингу (`KEY_TO_BINDINGS` — карта одного
  победителя), и мод, потерявший слот, становится **недостижим** — а починить бинд можно только в меню,
  которое он больше не открывает (Stage 62).
- Right Shift **открывает и закрывает** меню; закрытие — обратная анимация + мгновенный сырой
  GLFW-граб курсора и `MouseAccessor.cursorLocked` (НЕ `Mouse.lockCursor()` — в 1.21.1 он
  вызывает `setScreen(null)` и убивает анимацию). ESC закрывает только поповер.

## 5. Конфиг (ClubConfig)

- Файл: `.minecraft/config/club_settings.json` (в дев-режиме — `run/config/...`), Gson pretty-print.
- Грузится один раз на старте; **сохраняется при каждом изменении из GUI** (`ClubConfig.save()`).
  Слайдеры применяют значение live, а пишут один раз на отпускание (`Slider.onRelease`, Stage 29).
- **Запись асинхронная и атомарная (Stage 30):** `save()` сериализует на вызывающем потоке (рендер-тред
  мутирует INSTANCE — снимок обязан браться там же) и отдаёт строку одному фоновому писателю
  (`club-config-io`); тот пишет во `*.<pid>.tmp` и атомарно двигает поверх файла. Краш посреди записи
  больше не оставляет обрезанный JSON. `pid` в имени — потому что два клиента на одном `.minecraft`
  публиковали друг другу смесь двух записей.
- **Нечитаемый ≠ битый.** Только ошибка ПАРСИНГА означает порчу: файл сохраняется как
  `club_settings.json.corrupt` (и не затирает предыдущий такой). `IOException` (файл держит OneDrive,
  облачный плейсхолдер не подтянулся) — это НЕ порча: конфиг остаётся на диске, сессия идёт на
  дефолтах, а **запись выключается** (`readOnly`), чтобы не превратить сбой в потерю данных.
- **`version` + `migrate()` — сейчас v9** (`ClubConfig.java:23`). Ветки миграции: v1–v3 (позиции/масштабы
  HUD, перенос одноручных полей), v4 (мастер-тумблеры), v5 (Info HUD), v6 (кит v0.1 + чип Sprint),
  v7 (снять toggle-бинд с hold-модулей), v8 (`R16_9` → `AUTO`), v9 (позиции HUD помечаются легаси —
  холст сменил единицы, конверсия происходит на первом кадре, см. `hud/HudSpace`).
  **При добавлении полей — поднимай `version` до 10 и добавляй ветку `if (version < 10)`**; ветки до
  девятой уже заняты. Боксёванные `Float`/`Integer`-шимы в схеме — **read-only для migrate()**, боевой
  код их не читает; `Integer space` боксован намеренно (только `null` умеет значить «файл старше вопроса»).
- `sanitize()` чинит null-подобъекты ДО `migrate()`. Новая секция обязана попасть и туда: `itemScroll`
  разыменовывается на каждом клике в контейнере, `perf` — при сборке карточки; `"itemScroll": null`
  из руки был бы NPE в горячем пути, а не «дефолт не тот».
- Секции: `hands`, `animations`, `zoom`, `toggleSprint`, `freelook`, `screenStretch`, `moduleBinds`,
  `itemScroll`, `perf`, `hud`, флаги `noHurtCam/noFireOverlay/noBobbing/fullbright`.

## 6. Миксины (16) — и правило, которое надо знать ДО первого

`src/main/resources/club.mixins.json`, все клиентские, `"required": true`, `defaultRequire: 1`.

> ### `@Redirect` — ЭКСКЛЮЗИВНАЯ ЗАЯВКА. Не пиши его.
>
> Два `@Redirect` на одной инструкции — это заявка «инструкция моя»: проигравший миксин **не
> применяется**, а при `"required": true` игра **отказывается запускаться**. Не у тебя — у игрока,
> который поставил Club рядом с чужим модом. `changeLookDirection` (зум/фрилук/фрикам/shoulder-surfing)
> и `Particle.buildGeometry` (любой частичный лимитер) — самые оспариваемые вызовы в клиенте.
> Поэтому оба наших хука на них — **`@WrapOperation`** (MixinExtras): врапы **композируются**, каждый
> применяет своё преобразование, и клиент постороннего человека стартует. Правило записано в
> `MixinMouse.java:29-42` и оплачено `MixinParticleManager` — он был единственным `@Redirect` в проекте
> и был бы первым, кто не дал бы запуститься чужой сборке.
>
> Отсюда же общий порядок предпочтений: `@Accessor`/`@Invoker` (ничего не занимают) →
> `@ModifyReturnValue`/`@WrapOperation` → `@Inject` → **никогда** `@Redirect`/`@Overwrite`.

| Миксин | Что делает |
|---|---|
| `MixinHeldItemRenderer` | руки + кастомная анимация удара (**минное поле**, см. §8) |
| `MixinLivingEntity` | масштаб длительности свинга (`speed`) |
| `MixinGameRenderer` | NoHurtCam / NoBobbing / screen-stretch проекция / zoom `getFov` / сброс lightmap-scope раз в кадр |
| `MixinInGameHud` | скрыть ванильный оверлей эффектов — **только пока наш реально рисуется** (Stage 62) |
| `MixinInGameOverlayRenderer` | NoFireOverlay |
| `MixinMouse` | колесо→зум-фактор; демпфирование обзора при зуме; freelook-перехват (`@WrapOperation`) |
| `MixinCamera` | freelook: свободные yaw/pitch в `Camera.update` |
| `MixinSimpleOption` | fullbright: гамма отвечает 15.0 (read-side) |
| `MixinLightmapTextureManager` | окно, В КОТОРОМ это разрешено: гамма подменяется только внутри lightmap, никогда при записи `options.txt` |
| `MouseAccessor` | `cursorLocked` для закрытия меню (ловушка `Mouse.lockCursor`) |
| `MixinHandledScreenAccessor` | **весь** мixin-след Item Scroll: `@Accessor`/`@Invoker`, ноль инъекций |
| `MixinParticleManager` | `@WrapOperation` на `buildGeometry` → `ParticleCull` |
| `MixinBlockEntityRenderDispatcher` | `@Inject` на `render` → `BlockEntityCull` (переживает Sodium: тот меняет итерацию, но зовёт тот же диспетчер) |
| `MixinWorldRendererFrustum` | забирает фрустум ГЛАВНОЙ камеры в момент его постройки (поле читать нельзя: Sodium его затеняет, Iris гоняет второй проход солнцем) |
| `WorldRendererAccessor` | `regularEntityCount` — единственный счётчик, который не врёт (`blockEntityCount` в 1.21.1 — мёртвое поле) |
| `MixinMinecraftClientFps` | `getFramerateLimit` → `BackgroundThrottle` |

## 7. Приборы (это не тесты — это отдельные программы)

Инертны, пока не взведена env-переменная. Живут в `com/club/harness/`.

| Прибор | Как запустить | Что делает |
|---|---|---|
| **Харнесс** | `CLUB_HARNESS=1 ./gradlew runClient --args="--quickPlaySingleplayer club-harness-world"` | Самоуправляемый клиент: гоняет асерты и сцены (открывает экраны, жмёт им клавиши, снимает фреймбуфер), пишет `run/club-harness-report.txt` + `run/screenshots/club-*.png` и выходит. **Должен быть зелёным** (последний прогон на main: 94 passed, 0 failed). |
| **Бенч** | `CLUB_BENCH=1 ./gradlew runClient` | Арена с фиксированным сидом, два плеча (GPU-bound / CPU-bound), **чередование ON/OFF в одной сессии**, парные разности. Отчёт → `run/club-bench-report.txt`. Умеет сказать **INVALID**, а не соврать зелёным. |
| **Промо** | `CLUB_PROMO=1 ./gradlew runClient -PclubCompat -PclubIris` | Сам создаёт мир, находит горы/рощу, ставит закат, гасит ванильный HUD, снимает кадры галереи. |

Мира `club-harness-world` может не быть: `cp -r "run/saves/Новый мир" run/saves/club-harness-world`.

**Почему это центральный механизм проекта, а не украшение.** Мод дважды печатал на своей странице цифры,
которые прибор не мог повторить (0.45 мс — мерили загрузку машины; «43→11 GL-вызовов» — счётчик не видел
иконки), и оба раза их снимали. За цикл оптимизации **шесть предсказаний подряд** были опровергнуты
измерением. Отсюда правила, зашитые в `ClubBench`: миллисекунда — это машина, доля кадра — это мод;
ассертим счётчики, репортим время; сцена, которой нечего резать, — это **невалидный прогон**, а не
«фича не даёт выигрыша». Подробно — `docs/PERF.md`.

## 8. Совместимость с другими модами (известные ограничения)

- **`MixinHeldItemRenderer`** (анимации рук) перехватывает и пере-применяет свинг по рукам —
  почти наверняка конфликтует с другими модами анимаций от первого лица (First Person Model,
  Better Combat, аналогичные «hand/viewmodel» моды). Graceful-disable плагина миксинов нет:
  при жалобе на «двойную/сломанную анимацию» первым делом проверяй соседей по этому миксину.
  Это **сознательная противоположность** Item Scroll, у которого инъекций нет вообще.
- **`MixinGameRenderer`** (No Hurt Cam / No Bobbing / Screen Stretch) — модифицирует projection;
  моды камеры/шейдеры могут пересекаться. Screen Stretch с чёрными полосами намеренно кроет
  ванильный чат/хотбар в полосах (см. ANIMATIONS.md §7).
- **Sodium** — владеет рендером мира. Мы с ним не соревнуемся: культинга сущностей у нас **нет**
  (был, показал −22% и **удалён** — стирал фантомов в открытом небе). BE-культинг переживает Sodium,
  потому что тот меняет итерацию, но зовёт тот же диспетчер.
- **Iris** — гоняет второй проход глазами солнца. Любой культинг, привязанный к главной камере, обязан
  спросить `IrisCompat.inShadowPass()`, иначе стирает **тени**. Iris ещё и делит `renderParticles`
  на два прохода: посчитанное там считается на вызов, а не на кадр.
- **Item Scroller / Mouse Tweaks / Inventory Profiles Next / Mouse Wheelie** — Item Scroll видит их
  через `isModLoaded` и **стоит в стороне**, честно объясняя причину через `ModuleNotices`. Два мода на
  одном жесте = двойной перенос и десинк, который спишут на Club.
- **CI** — `.github/workflows/build.yml` собирает и гоняет тесты на JDK 21 при каждом push/PR
  (ubuntu-24.04, `./gradlew build`); `release.yml` — сборка релиза по тегу.

## 9. Швы: как добавить свой модуль, не подравшись

Пять файлов — **общие**. Логика модуля живёт **в его пакете**; в общий файл добавляется **одна строка
под своим якорем**, и ничего вокруг не переформатируется (иначе ветки дерутся на merge).
Полностью — `docs/NEXT-PLAN.md` §4.

| Шов | Якорь | Что кладут |
|---|---|---|
| `config/ClubConfig.java` | `[SEAM:config]` (`:39`) | одну строку: `public Perf perf = new Perf();` — поля в своём классе, в своём пакете. Плюс null-guard в `sanitize()` |
| `ClubClient.java` | `[SEAM:init]` (`:96`) | одну строку `MyModule.init();` |
| `ui/menu/MenuContent.java` | `[SEAM:cards]` (`:125`) | одну строку — фабрику карточки из своего пакета. **Больше в этом файле ничего** (меню заморожено) |
| `harness/ClubHarness.java` | `[SEAM:checks]` (`:533`) | свой блок асертов, каждый в своём `step(...)` |
| `resources/club.mixins.json` | массив `client` | имя миксина **в конец** |

**Строка «почему модуль стоит» — без якоря вообще:** `ModuleNotices.register("My Module", () -> …)`
из `init()` своего модуля. `MenuContent.notice()` не трогается. Саплаер зовётся каждый кадр открытого
поповера — он обязан быть дешёвым и читать ЖИВОЕ состояние: протухшее пояснение хуже отсутствующего,
потому что карточка тогда врёт уверенно. (Механизм жив на будущее; регистраций сейчас нет — перф-карточки
Particles/Block Entities, которые им пользовались, вшиты и убраны из меню.)

## 10. Функциональные области (куда смотреть)

| Область | Доки | Ключевые файлы |
|--------|------|----------------|
| Внешний вид (меню, цвет, шрифты, контролы) | [UI-V2-MENU.md](UI-V2-MENU.md) (философия — [DESIGN.md](DESIGN.md)) | `ui/theme/Tokens`, `ui/menu/*`, `ui/component/widget/*` |
| Анимации рук + твики вида | [ANIMATIONS.md](ANIMATIONS.md) | `modules/animations/*`, `mixin/MixinHeldItemRenderer`, `mixin/MixinGameRenderer` |
| HUD и их настройки | [HUD-LANGUAGE.md](HUD-LANGUAGE.md) | `ui/hud/*`, `hud/PixelIcons`, `hud/HudSpace`, `ClubConfig.Hud` |
| Item Scroll (жесты, композиция кликов) | [ITEMSCROLL.md](ITEMSCROLL.md) | `modules/itemscroll/*`, `mixin/MixinHandledScreenAccessor` |
| Правила сервера (Club отходит в сторону) | [спека](superpowers/specs/2026-07-16-server-policy-design.md) | `policy/*`, гейты в `modules/itemscroll/*` |
| Оптимизация (что делаем и чего НЕ делаем) | [PERF.md](PERF.md) | `modules/perf/*`, четыре perf-миксина, `harness/ClubBench` |

## 11. Соглашения и правила, из-за которых PR закроют

> Канонический список для внешнего контрибьютора — **[CONTRIBUTING.md](../CONTRIBUTING.md)** (по-английски).
> Здесь — то же самое плюс внутренние соглашения; расхождений быть не должно.

**Стиль кода и текста**

- Код, комментарии в коде и строки интерфейса — **English**. Документация в `docs/` — **по-русски**.
- Комментарий объясняет **почему**, а не пересказывает строку.
- Никакого мусора в UI: путей конфига, debug-координат, серых филлер-подписей.
- Цвета/размеры — через токены `ui/theme/Tokens` (тема ClubDark), не хардкодом.

**Правила, нарушение которых закрывает PR**

1. **Дизайн заморожен.** Плоско, без glass/blur/glow/тяжёлых теней. Градиент — **только** на подчёркивании
   активной вкладки, **никогда** на тексте. Палитра — `docs/DESIGN.md`.
2. **Меню и поповеры заморожены.** `ui/menu/**` и `ui/component/**` не трогаются: владелец посмотрел три
   альтернативные конструкции и решил не менять ничего. Единственное исключение — одна строка под
   `[SEAM:cards]`. Нужен свой сложный экран — делай **отдельный** `Screen` (прецеденты: HUD Editor,
   `GestureScreen`).
3. **Не печатать число, которое прибор не повторит.** Ни в README, ни в CHANGELOG, ни в javadoc. Число
   называется **вместе со сценой**, в которой оно измерено, и берётся из `ClubBench`/`ClubHarness`.
   Не измерил — не пиши. Мод уже трижды был пойман на этом.
4. **Это не чит-клиент.** Killaura, ESP, reach, X-ray, aim-assist — нет. Freelook двигает камеру и
   **никогда** прицел; Item Scroll — утилита, но она честно шлёт `ClickSlotC2SPacket` на каждый клик,
   и это написано и в коде, и на странице мода.
5. **Запрет «на двери» ≠ запрет в действии.** Проверка, стоящая только в UI, обходится правкой JSON.
   `Gestures.reserved()` спрашивался лишь при **биндинге** — и строка `"MOVE_EVERYTHING": "LMB"`,
   вписанная руками, отключала обычный левый клик во всех контейнерах игры. Теперь отказывает **путь
   чтения** (`Gestures.actionFor`). Барьер, который однажды обойдут, — это ноль барьеров.
6. **Виджеты Club коммитят на РЕЛИЗЕ, а не на нажатии.** Захват жеста, армленный на press, привязывал сам
   себя к взводящему клику.
7. **Проверять в игре.** «Должно работать» — не аргумент. Харнесс зелёный, скриншоты приложены; владелец
   смотрит попиксельно.
8. **Коммит после каждого этапа**, локально, без push (если не просили). Сообщение объясняет *почему*.
