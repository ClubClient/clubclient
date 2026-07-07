# Club — Architecture & Build

> Общая структура проекта, сборка/запуск, конфиг и миксины. Точка входа для всего остального.

## 1. Что это за проект

**Club** — клиентский utility-мод для Minecraft на **Fabric 1.21.1** (Yarn mappings, **Java 21**).
Пакет `com.club`. Меню открывается на **Right Shift**. «Премиальный» first-person клиент: кастомные
анимации/позиционирование рук, screen-stretch, визуальные твики вида и стильный HUD.

## 2. Сборка и запуск

```powershell
.\gradlew.bat compileJava     # быстрая проверка компиляции
.\gradlew.bat build           # сборка джара
.\gradlew.bat runClient       # запуск дев-клиента (окно MC)
```

- Дев-окружение: `run/` (мир `saves/Новый мир`, Sodium не установлен — `run/mods` пуст, голый fabric-api).
- Декомпилированный named MC jar (для проверки ванильных внутренностей):
  `~/.gradle/caches/fabric-loom/minecraftMaven/...minecraft-merged-1.21.1...jar` — извлечь класс и `javap -p -c`.

## 3. Карта пакетов

```
com.club
├── ClubMod / ClubClient            — инициализация; кейбинд Right Shift → ui.menu.ClubMenuScreen;
│                                     reload-листенер дуотон-иконок (PixelIcons)
├── config/ClubConfig               — модель настроек + load/save/migrate (Gson)
├── ui/                             — V2 UI-стек (MSDF/SDF)  → UI-V2-MENU.md
│   ├── backend/                    — MODERN/LEGACY рендер, шейдеры, атласы (единственное место low-level GL)
│   ├── component/ + widget/        — Component-дерево; Button/Toggle/Checkbox/Slider/Label/ScrollArea
│   ├── layout/ · motion/ · text/ · theme/ — размеры; Transition/Reveal/ValueTween; TextStyle; Tokens (ClubDark)
│   ├── menu/                       — ClubMenuScreen + MenuContent (боевое меню)  → UI-V2-MENU.md
│   ├── hud/                        — элементы HUD + HudCanvas + HudEditorScreen  → HUD-LANGUAGE.md
│   └── IconGlyph                   — SDF-иконки (PUA; HUD-глифы = фоллбек дуотона)
├── util/Mth                        — clamp/lerp/smooth/snap
├── hud/                            — HudManager (диспетчер) + PixelIcons (дуотон ванильных текстур,
│                                     DrawContext-шов) + PotionHud/TargetHud (data-only)
├── modules/
│   ├── animations/                 — AnimationType / Pose / AnimationModule  → ANIMATIONS.md
│   ├── hands/HandsModule           — масштаб/смещение рук
│   └── screenstretch/              — ScreenStretchModule / StretchPreset
└── mixin/
    ├── MixinHeldItemRenderer       — руки + кастомная анимация удара
    ├── MixinLivingEntity           — масштаб длительности свинга (speed)
    ├── MixinGameRenderer           — NoHurtCam / NoBobbing / screen-stretch проекция
    ├── MixinInGameHud              — скрыть ванильный оверлей эффектов
    ├── MixinInGameOverlayRenderer  — NoFireOverlay
    └── MouseAccessor               — cursorLocked для закрытия меню (ловушка Mouse.lockCursor)
```

Ресурсы: `assets/club/ui/font/msdf` (MSDF-атлас текста), `assets/club/ui/icon/msdf/icons.{png,json}`
(SDF-иконки, генератор в buildSrc), `assets/club/shaders/core/ui_*`, `assets/club/lang/*`,
`fabric.mod.json`, `club.mixins.json`. (TTF-исходники шрифтов — в `tools/fonts/`, в джар не идут.)

## 4. Точки входа

- `ClubMod` (main) — общая инициализация.
- `ClubClient` (client) — `Ui.init()` (шейдеры V2 — строго до стартового resource reload),
  `ClubConfig.load()`, `HudManager.init()`, reload-листенер `PixelIcons`, кейбинд
  `key.club.open_menu` (Right Shift), тик-хендлер открывает `ui.menu.ClubMenuScreen`.
- Right Shift **открывает и закрывает** меню; закрытие — обратная анимация + мгновенный сырой
  GLFW-граб курсора и `MouseAccessor.cursorLocked` (НЕ `Mouse.lockCursor()` — в 1.21.1 он
  вызывает `setScreen(null)` и убивает анимацию). ESC закрывает только поповер.

## 5. Конфиг (ClubConfig)

- Файл: `.minecraft/config/club_settings.json` (в дев-режиме — `run/config/...`), Gson pretty-print.
- Грузится один раз на старте; **сохраняется при каждом изменении из GUI** (`ClubConfig.save()`).
  Слайдеры применяют значение live, а пишут один раз на отпускание (`Slider.onRelease`, Stage 29).
- **Запись асинхронная и атомарная (Stage 30):** `save()` сериализует на вызывающем потоке и
  отдаёт строку одному фоновому писателю (`club-config-io`); тот пишет во `*.tmp` и атомарно
  двигает поверх файла. Краш посреди записи больше не оставляет обрезанный JSON. Нечитаемый
  файл при загрузке **сохраняется как `club_settings.json.corrupt`** (не перезаписывается
  молча) и только потом берутся дефолты. На `CLIENT_STOPPING` писатель дренится
  (`ClubConfig.close()`).
- `version` + `migrate()` — миграция старых файлов (текущая v5: v4 — мастер-тумблеры модулей,
  v5 — поля Info HUD `info/infoX/infoY/infoScale`). При добавлении полей — поднимай `version`
  и добавляй ветку в `migrate`. Боксёванные `Float`-шимы в схеме — **read-only для migrate()**,
  боевой код их не читает (задокументировано в Stage 29).
- Секции: `hands`, `animations`, `screenStretch`, флаги `noHurtCam/noFireOverlay/noBobbing`, `hud`.

## 6. Функциональные области (куда смотреть)

| Область | Доки | Ключевые файлы |
|--------|------|----------------|
| Внешний вид (меню, цвет, шрифты, контролы) | [UI-V2-MENU.md](UI-V2-MENU.md) (философия — [DESIGN.md](DESIGN.md)) | `ui/theme/Tokens`, `ui/menu/*`, `ui/component/widget/*` |
| Анимации рук + твики вида | [ANIMATIONS.md](ANIMATIONS.md) | `modules/animations/*`, `mixin/MixinHeldItemRenderer`, `mixin/MixinGameRenderer` |
| HUD и их настройки | [HUD-LANGUAGE.md](HUD-LANGUAGE.md) | `ui/hud/*`, `hud/PixelIcons`, `ClubConfig.Hud` |

## 7. Соглашения

- UI-лейблы — **English** (HANDS, ANIMATIONS, …); ответы/чат пользователю — **на русском**.
- Никакого мусора в UI: путей конфига, debug-координат, серых филлер-подписей.
- Цвета/размеры — через токены `ui/theme/Tokens` (тема ClubDark), не хардкодом.
- Премиальный, плоский, минималистичный стиль: без glass/blur/тяжёлых теней/декор-иконок.

## 8. Совместимость с другими модами (известные ограничения)

- **`MixinHeldItemRenderer`** (анимации рук) перехватывает и пере-применяет свинг по рукам —
  почти наверняка конфликтует с другими модами анимаций от первого лица (First Person Model,
  Better Combat, аналогичные «hand/viewmodel» моды). Graceful-disable плагина миксинов нет:
  при жалобе на «двойную/сломанную анимацию» первым делом проверяй соседей по этому миксину.
- **`MixinGameRenderer`** (No Hurt Cam / No Bobbing / Screen Stretch) — модифицирует projection;
  моды камеры/шейдеры могут пересекаться. Screen Stretch с чёрными полосами намеренно кроет
  ванильный чат/хотбар в полосах (см. ANIMATIONS.md §7).
- **CI** — `.github/workflows/build.yml` собирает и гоняет тесты на JDK 21 при push/PR
  (активируется при появлении GitHub-remote; сейчас репозиторий локальный).
