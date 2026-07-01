# Club — UI V2 (menu, HUD, widget system)

> Новый плоский UI-стек `com.club.ui` + меню «Variant D» + HUD. Сейчас это **dev-превью**, подключённое
> к реальному `ClubConfig`. Старое меню `gui/ClubScreen` (Right Shift) пока остаётся — V2 заменит его на
> Stage 5. Дизайн утверждён: `spec/UI-V2-DESIGN-CONCEPT.md` + статичные макеты `spec/mockups/`.

## 1. Как открыть / посмотреть

| Клавиша | Экран | Статус |
|---|---|---|
| **H** | `ui.devmenu.ClubMenuScreen` — меню V2 (rail │ сетка карточек │ настройки │ footer) | dev, временно |
| **J** | `ui.devhud.HudEditorScreen` — редактор HUD | dev, временно |
| **G** | `ui.devgallery.WidgetGalleryScreen` — лист виджетов | dev, временно |
| Right Shift | `gui.ClubScreen` — **боевое** старое меню | прод (до Stage 5) |

**Headless-скриншоты** (без рук, для проверки UI): запусти с env-флагом, экран сам откроется, снимет
кадры в `run/screenshots/` и остановит клиент:
```bash
CLUB_MENU=1   ./gradlew --no-daemon runClient   # меню (+ симуляция кликов: смена категории, выбор карточки)
CLUB_HUD=1    ./gradlew --no-daemon runClient   # in-game HUD + редактор
CLUB_GALLERY=1 ./gradlew --no-daemon runClient  # лист виджетов
```
(Рендер — реальный движок, не макет. `--no-daemon` нужен, чтобы env-флаг дошёл до форкнутого JVM игры.)

## 2. Карта файлов (новое)

```
com.club.ui
├── Icon                         — процедурные line-иконки (только circle/ring/axis-line/rect — без
│                                  диагоналей: бэкенд рисует диагональ как bbox). COMBAT/MOVEMENT/RENDER/
│                                  PLAYER/WORLD/EXPLOIT/HUD/SETTINGS/SEARCH
├── layout/Grid                  — фикс-колоночная сетка (перенос по строкам)
├── component/widget/
│   ├── WidgetPaint              — ЕДИНЫЙ источник визуального языка: surface()/controlTrack()/puck()/
│   │                              whitePuck()/focusRing()/focusRingCircle()/hoverWash()/pressOverlay()/
│   │                              clipRounded(); константы HANDLE_*_GROW
│   ├── ModuleCard               — карточка модуля (иконка + имя + тумблер + desc + счётчик настроек;
│   │                              состояние через акцент: вкл = верхняя кромка, selected = рамка)
│   ├── CategoryItem             — строка rail (иконка + лейбл + счётчик; активный фон)
│   ├── Dropdown                 — клик = следующий вариант (поповер — позже)
│   ├── Keybind                  — клик → «слушает» → следующая клавиша; ESC снимает
│   ├── TextField                — поиск (иконка + ввод; нужен фокус)
│   └── Button/Toggle/Checkbox/Slider/Panel/Card/Window/ScrollArea/Label/Divider — база (M2.2)
├── devmenu/                     — [ВРЕМЕННО] меню-превью
│   ├── ClubMenuScreen           — экран: rail/сетка/детали/footer, поиск, скролл, моушн-индикатор
│   ├── MenuModel                — Category / Mod / Setting (live-bind: getter+setter)
│   ├── MenuContent              — строит категории ИЗ ClubConfig (двусторонняя привязка + save())
│   └── MenuBootstrap            — headless-съёмка (CLUB_MENU)
└── devhud/                      — [ВРЕМЕННО] HUD-превью
    ├── HudView                  — отрисовка элементов (watermark/arraylist/target/keystrokes/info/effects/toast)
    ├── HudPreviewScreen         — in-game HUD
    ├── HudEditorScreen          — редактор (тулбар + ручки выделения + панель свойств)
    └── HudBootstrap             — headless-съёмка (CLUB_HUD)
```
`ClubClient` регистрирует кейбинды H/J/G и `*Bootstrap.init()` (всё помечено `[… TEMPORARY, remove before merge]`).

## 3. Дизайн-язык (Variant D)

- **Композиция:** rail категорий (иконка+счётчик, активная подсветка + скользящий акцентный индикатор) │
  сетка карточек-модулей │ постоянная панель настроек (связана с выбранной карточкой акцентом) │ status-footer.
- **Глубина — плоская:** только тон-ступени ink-рампы + 1px хайрлайны. **Без shadow/blur/glow/градиентов.**
- **Акцент — только сигнал** («одна акцентная нить»): активная категория, вкл-состояния, заливка слайдера,
  фокус, primary. Из хрома (скроллбар) убран.
- **Токены:** всё через `Tokens` (palette/surface/accent/border/radius/spacing/type/motion). Менять токены —
  только с согласования (см. память `ui-v2-redesign-direction`).
- Слайдер-ручка: компактная светло-акцентная (`accentHi`) с тонким тёмным кольцом — НЕ белый «puck» (отклонено).

## 4. Привязка к реальному конфигу

`MenuContent.categories()` строит дерево из `ClubConfig.get()`:

| Категория | Модули → настройки (всё bound на `ClubConfig`) |
|---|---|
| **Visual** | Hands (enabled + R/L scale·X·Y·Z), Animations (enabled + Type/Speed/Amplitude), Screen Stretch (enabled + Preset/Black bars) |
| **View** | No Hurt Cam / No Bobbing / No Fire Overlay (флаги) |
| **HUD** | Armor (scale/percent/vertical), Potion (scale/horizontal), Target (scale/distance) |

Каждый `Setting` читает через getter и пишет через setter, который вызывает **`ClubConfig.save()`**. Меню =
тонкий вид над конфигом, без дублирования состояния. Dropdown-опции — из `AnimationType`/`StretchPreset` (`.label()`).

## 5. Статус и что осталось

**Готово и проверено в движке:** меню (rail/сетка/детали/footer/поиск/скролл/выбор/тумблеры), привязка к
ClubConfig, HUD-система + редактор, моушн (скользящий индикатор rail; рост ручек toggle/slider; анимации
check/knob). Юнит-тесты зелёные.

**Осталось / отложено (для будущих чатов):**
- **Tabular figures** — нужен флаг `tabular` через `UiText → ModernText/LegacyText → TextLayout` (фикс-ширина
  цифр). Отложено: несколько точек в text-стеке, выигрыш малый (значения и так right-align.
- **Моушн (опц.):** кросс-фейд контента при смене категории/выбора (требует вынести grid/detail из `root` под
  отдельный `pushOpacity`).
- **Stage 5 (интеграция):** заменить `gui/ClubScreen` на V2, подключить HUD к живым данным (FPS/координаты/
  цель/модули вместо demo), удалить dev-scaffolding (devmenu/devhud/devgallery + кейбинды H/J/G + бутстрапы).
- **Известные лимиты рендера (`backend/ModernBackend`):** `pushRoundedClip` = прямоугольный scissor (радиус
  игнорируется) → клип контента не скруглён (Window/Panel/Card обходят это формой/инсетами); диагональные
  линии аппроксимируются bbox → иконки используют только circle/ring/axis-line/rect, галочка чекбокса
  приблизительная. Это кандидаты на shader-маску/rotated-capsule SDF.

## 6. Как расширять

- **Новый модуль/настройка в меню:** добавь в `MenuContent` (`Mod` + `Setting` с get/set на `ClubConfig`).
- **Новый тип настройки:** добавь подкласс `MenuModel.Setting` с `control()`.
- **Новый виджет/состояние:** рисуй через `WidgetPaint` (единый язык), не дублируй последовательности вызовов.
- **Иконка:** добавь значение в `Icon` (только circle/ring/axis-line/rect/roundedRect).
