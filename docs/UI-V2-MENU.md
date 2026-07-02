# Club — UI V2 (menu, HUD, widget system)

> **Актуализировано 2026-07-02.** Плоский UI-стек `com.club.ui` — **боевой**: меню на Right Shift =
> `com.club.ui.menu.ClubMenuScreen` (Stage 6), legacy `gui/ClubScreen` удалён (Stage 7, `54cc350`).
> Dev-превью (`devmenu`/`devhud`/`devgallery`, кейбинды H/J/G, headless-флаги `CLUB_*`) удалены вместе
> со Stage 5-интеграцией. История ранних решений — в git до `f40c887`.

## 1. Как открыть

| Действие | Результат |
|---|---|
| **Right Shift** | `ui.menu.ClubMenuScreen` — боевое меню (rail категорий │ сетка карточек) |
| Misc → HUD Editor | `ui.hud.HudEditorScreen` — редактор HUD-позиций |
| ESC | закрыть (единственный способ; крестика/minimize нет — по дизайну) |

## 2. Текущее меню (Stage 6+, утверждено)

- **Компактное draggable-окно** 660×380 (не полноэкранное): перенос **только** за грип-пилюлю 44×5
  над верхней кромкой; всегда кламп полностью на экран; позиция сохраняется в `ClubConfig.menuX/menuY`
  (−1 = по центру), запись — один раз на отпускание.
- **Фон не затемняется и не блюрится** (`renderBackground` пуст): мир виден, настройки применяются live.
  Blur добавлялся (`d938f55`) и **отменён владельцем** (`49bc69b`) — не возвращать.
- **Карточки** (`ModuleTile`, приватный класс экрана): только имя, без иконок и тумблеров.
  **ЛКМ** = вкл/выкл (заливка/кромка/имя едут одним eased-фактором, кромка = accent↔violet);
  **ПКМ** = плавающий поповер настроек возле карточки (Reveal grow-in/out + ValueTween высоты,
  контент клипается по eased-высоте; раскрытый Dropdown скрывает соседние строки — ничего не «телепортируется»).
- **Rail**: текст + процедурная иконка, скользящий индикатор-`Transition`, hover/active цвет ease.
- **Поиск** (`SearchField`, приватный класс экрана): live-фильтр по имени+описанию модуля.
- Вход экрана: подъём окна на 12px + fade грипа (без scale, без скрима).

Экранные композиты (`ModuleTile`/`SearchField`/`OptionRow`/`Pane`) — **сознательно приватные** в
`ClubMenuScreen`, а не виджеты: подгонка под меню важнее переиспользования.

## 3. Карта файлов

```
com.club.ui
├── Icon                         — процедурные line-иконки (только circle/ring/axis-line/rect — без
│                                  диагоналей: бэкенд рисует диагональ как bbox)
├── menu/
│   ├── ClubMenuScreen           — боевой экран: окно/грип/rail/сетка/поиск/поповер (вся геометрия и ввод)
│   └── MenuContent              — ЧИСТЫЕ ДАННЫЕ: Category → Module → Setting (sealed), live-bind на
│                                  ClubConfig (getter + setter, setter вызывает save())
├── component/widget/            — живые виджеты: Button, Toggle, Checkbox, Slider, Dropdown, Label,
│                                  ScrollArea (+ база Control, WidgetPaint, BoolConsumer/FloatConsumer)
└── hud/                         — HudCanvas/HudElement + Target/Effects/Armor/Info, HudEditorScreen,
                                   HudPaint/HudText/HudSprites/HudSnap/Decals (см. HUD-LANGUAGE.md)
```

**Удалено за неиспользованием (2026-07-02):** виджеты `Window`, `ModuleCard`, `CategoryItem`,
`TextField`, `Card`, `Panel`, `Keybind`, `Divider` и мёртвые члены `WidgetPaint`
(`elevation()`/`flatSurface()`/`clipRounded()`/`ACCENT_RIM`) — ни одной продакшн-ссылки: меню рисует
собственные окно/карточки/rail/поиск на уровне экрана. Нужны снова — восстанавливаются из git
(последнее состояние: коммит перед этой чисткой).

## 4. Дизайн-язык (Variant D) — действует

- **Композиция:** rail категорий (иконка + скользящий акцентный индикатор) │ сетка карточек.
  Постоянной панели настроек нет — настройки в ПКМ-поповере (решение Stage 6).
- **Глубина — плоская:** только тон-ступени ink-рампы + 1px хайрлайны. **Без shadow/blur/glow/градиентов.**
  (Toggle тоже плоский — градиент+glow из M2.2 §3.8 сняты, см. амендмент в UI-V2-STAGE2-M2.2-SPEC.md.)
- **Акцент — только сигнал** («одна акцентная нить»): активная категория, вкл-состояния, заливка слайдера,
  фокус, primary. Из хрома (скроллбар) убран.
- **Токены:** всё через `Tokens` (palette/surface/accent/border/radius/spacing/type/motion). Менять токены —
  только с согласования (см. память `ui-v2-redesign-direction`).
- Слайдер-ручка: компактная светло-акцентная (`accentHi`) с тонким тёмным кольцом — НЕ белый «puck» (отклонено).

## 5. Привязка к реальному конфигу

`MenuContent.build()` строит дерево из `ClubConfig.get()`:

| Категория | Модули → настройки (всё bound на `ClubConfig`) |
|---|---|
| **Combat** | Animations (enabled + Type/Speed/Amplitude) |
| **Visuals** | Screen Stretch (enabled + Preset/Black Bars), No Hurt Cam, No Fire Overlay, No Bobbing |
| **Player** | Hands (enabled + вкладки Right/Left: Scale·X·Y·Z) |
| **Misc** | HUD Editor (action), Hide Vanilla Effects |

Каждый `Setting` читает через getter и пишет через setter, который вызывает **`ClubConfig.save()`**. Меню =
тонкий вид над конфигом, без дублирования состояния. Dropdown-опции — из `AnimationType`/`StretchPreset` (`.label()`).

## 6. Статус и что осталось

**Готово и проверено:** боевое меню (Stage 6/7), моушн-слой (Stage 9), HUD «light structure» + Armor V2
(Stage 10), Hero Target эталон (HUD-LANGUAGE.md §8). Юнит-тесты зелёные.

**Осталось / отложено (для будущих чатов):**
- **Tabular figures** — нужен флаг `tabular` через `UiText → ModernText/LegacyText → TextLayout` (фикс-ширина
  цифр). Отложено: несколько точек в text-стеке, выигрыш малый (значения и так right-align;
  временный шим — `hud/HudText`, расширяет только «1»).
- **Моушн (опц.):** кросс-фейд контента при смене категории/поиске (лимит: текст не фейдится через
  `pushOpacity` — только `Color.scaleAlpha`).
- **Известные лимиты рендера (`backend/ModernBackend`):** `pushRoundedClip` = прямоугольный scissor (радиус
  игнорируется); диагональные линии аппроксимируются bbox → иконки используют только circle/ring/axis-line/rect,
  галочка чекбокса приблизительная. Кандидаты на shader-маску/rotated-capsule SDF — станут актуальны,
  если понадобятся диагональные иконки.

## 7. Как расширять

- **Новый модуль/настройка в меню:** добавь в `MenuContent` (`Module` + `Setting` с get/set на `ClubConfig`).
- **Новый тип настройки:** новый record в sealed `MenuContent.Setting` + ветка в `ClubMenuScreen.buildControl`.
- **Новый виджет/состояние:** рисуй через `WidgetPaint` (единый язык), не дублируй последовательности вызовов.
- **Иконка:** добавь значение в `Icon` (только circle/ring/axis-line/rect/roundedRect).
