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
├── ClubMod / ClubClient            — инициализация; кейбинд Right Shift → ClubScreen
├── config/ClubConfig               — модель настроек + load/save/migrate (Gson)
├── gui/
│   ├── ClubScreen                  — главное меню (вкладки/список/панель)  → DESIGN.md
│   ├── HudEditorScreen             — редактор позиций HUD                  → HUDS.md
│   ├── Theme                       — цвет/spacing токены                   → DESIGN.md
│   ├── Icons                       — только функциональные line-иконки (chevron, brandDot)
│   └── components/                 — SliderWidget, DropdownWidget, ToggleWidget, SegmentedWidget, ButtonC
├── util/
│   ├── ClubFont                    — роли шрифтов Inter (cat/name/hud/list/desc/small)  → DESIGN.md
│   ├── RenderHelper                — rounded rect, градиенты, glow, hairline, radialGlow
│   └── Mth                         — clamp/lerp/smooth/snap
├── hud/                            — HudManager + ArmorHud/PotionHud/TargetHud + HudStyle  → HUDS.md
├── modules/
│   ├── animations/                 — AnimationType / Pose / AnimationModule  → ANIMATIONS.md
│   ├── hands/HandsModule           — масштаб/смещение рук
│   └── screenstretch/              — ScreenStretchModule / StretchPreset
└── mixin/
    ├── MixinHeldItemRenderer       — руки + кастомная анимация удара
    ├── MixinLivingEntity           — масштаб длительности свинга (speed)
    ├── MixinGameRenderer           — NoHurtCam / NoBobbing / screen-stretch проекция
    ├── MixinInGameHud              — скрыть ванильный оверлей эффектов
    └── MixinInGameOverlayRenderer  — NoFireOverlay
```

Ресурсы: `assets/club/font/inter_*.ttf` + `club_*.json` (провайдеры), `assets/club/lang/*`,
`fabric.mod.json`, `club.mixins.json`.

## 4. Точки входа

- `ClubMod` (main) — общая инициализация.
- `ClubClient` (client) — `ClubConfig.load()`, `HudManager.init()`, регистрация кейбинда
  `key.club.open_menu` (Right Shift), тик-хендлер открывает `ClubScreen`.
- Меню закрывается **только по ESC** (стандартный `Screen`); кнопок окна нет.

## 5. Конфиг (ClubConfig)

- Файл: `.minecraft/config/club_settings.json` (в дев-режиме — `run/config/...`), Gson pretty-print.
- Грузится один раз на старте; **сохраняется немедленно при каждом изменении из GUI** (`ClubConfig.save()`).
- `version` + `migrate()` — миграция старых файлов (текущая v4: добавлены мастер-тумблеры модулей).
  При добавлении полей — поднимай `version` и добавляй ветку в `migrate`.
- Секции: `hands`, `animations`, `screenStretch`, флаги `noHurtCam/noFireOverlay/noBobbing`, `hud`.

## 6. Функциональные области (куда смотреть)

| Область | Доки | Ключевые файлы |
|--------|------|----------------|
| Внешний вид (меню, цвет, шрифты, контролы) | [DESIGN.md](DESIGN.md) | `gui/Theme`, `util/ClubFont`, `gui/ClubScreen`, `gui/components/*` |
| Анимации рук + твики вида | [ANIMATIONS.md](ANIMATIONS.md) | `modules/animations/*`, `mixin/MixinHeldItemRenderer`, `mixin/MixinGameRenderer` |
| HUD и их настройки | [HUDS.md](HUDS.md) | `hud/*`, `gui/HudEditorScreen`, `ClubConfig.Hud` |

## 7. Соглашения

- UI-лейблы — **English** (HANDS, ANIMATIONS, …); ответы/чат пользователю — **на русском**.
- Никакого мусора в UI: путей конфига, debug-координат, серых филлер-подписей.
- Цвета/размеры — через токены `Theme`/`ClubFont`, не хардкодом.
- Премиальный, плоский, минималистичный стиль: без glass/blur/тяжёлых теней/декор-иконок.
