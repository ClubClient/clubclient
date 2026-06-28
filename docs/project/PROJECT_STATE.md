# PROJECT_STATE — Club

> Главный факт-документ проекта и индекс долговременной памяти. Прочитав его + файлы из «порядка чтения»,
> новый ИИ понимает проект без истории чатов. Источник истины — эта папка `docs/project/` + канонические
> `docs/UI-V2*.md`. Пояснения — RU, идентификаторы/код — EN.

## Порядок чтения для нового чата
1. **[AI_CONTEXT.md](AI_CONTEXT.md)** — правила работы, стиль разработки, как взаимодействовать с пользователем.
2. **[CURRENT_TASK.md](CURRENT_TASK.md)** — где мы сейчас (живой статус, читать первым делом).
3. **[HANDOVER.md](HANDOVER.md)** — нарратив: что это, почему так, что дальше, что обязательно прочитать.
4. По нужде: [ARCHITECTURE.md](ARCHITECTURE.md), [ROADMAP.md](ROADMAP.md), [DECISIONS.md](DECISIONS.md),
   [FILE_STRUCTURE.md](FILE_STRUCTURE.md), [STYLE_GUIDE.md](STYLE_GUIDE.md), [PERFORMANCE.md](PERFORMANCE.md).
5. Канон контрактов/кода: **`docs/UI-V2.md`** (контракты), `docs/UI-V2-RISKS.md`, `docs/UI-V2-PERF.md`,
   `docs/UI-V2-STAGE1-PLAN.md` (запись плана Stage 1). Существующий клиент: `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, `docs/HUDS.md`, `docs/ANIMATIONS.md`.

## Что за проект
**Club** — клиентский utility-мод для Minecraft на **Fabric 1.21.1** (Yarn, **Java 21**), пакет `com.club`.
Премиальный first-person клиент: кастомные анимации/позиционирование рук, screen-stretch, визуальные твики, HUD,
меню (Right Shift). Существующая часть описана в `docs/ARCHITECTURE.md`.

## Философия
Интерфейс должен ощущаться **дорогим, чистым, минималистичным, игровым, современным** — уровень
Pulse Visual / Raycast / Linear / премиальных оверлеев. При каждом решении: «делает дороже или просто добавляет
деталей?» — если второе, элемент удаляется. (Визуальные токены/стиль — `docs/DESIGN.md`, актуально с Stage 2.)

## Текущий статус
**Stage 1 (рендер-фундамент UI V2) — ЗАВЕРШЁН и в `main`** (merge `ce27dab`, 2026-06-26). `gradlew build` зелёный.
Подробности этапов — [ROADMAP.md](ROADMAP.md), статус задачи — [CURRENT_TASK.md](CURRENT_TASK.md).

## Что завершено (Stage 1)
Новый resolution-independent рендер-стек в `com.club.ui`: контракты `UiRenderer`/`UiText`/`UiContext`/`Ui`;
`ModernBackend` (аналитический SDF: rect/rounded/border/gradient/shadow/glow/line/circle/clip/opacity) +
`ModernText` (MSDF, слои Atlas/Metrics/Layout/Renderer) + `LegacyBackend`/`LegacyText` (fallback); core-шейдеры
`ui_sdf_shape`/`ui_msdf_text`; замороженные MSDF-атласы Inter (Latin+Cyrillic) + Gradle `genMsdfAtlas`; JUnit-тесты
+ архитектурный guard-тест. Рантайм проверен (шейдеры компилируются, текст резкий @1×/500×, graceful fallback без
краша). PoC-слой удалён; фундамент сейчас — **library-only** (без in-client точки входа).

## Что НЕЛЬЗЯ менять (frozen)
- **Сигнатуры контрактов** `UiRenderer`/`UiText`/`UiContext`/`Ui` (компоненты строятся на них; см. `docs/UI-V2.md` §3–5).
- **Backend-only hard rule** — низкоуровневый рендер только в `com.club.ui.backend.*` (enforced `ArchitectureRuleTest`).
- **Charset/параметры MSDF-атласов** (Latin+Latin-1+Cyrillic+symbols, `-size 40 -pxrange 6`); атласы меняются только
  через `genMsdfAtlas`, не руками.
- **Существующие `gui/*`, `hud/*`, `Theme`, `ClubFont`, `RenderHelper`** — не трогаем до **Stage 5**.
- `ui_*` core-шейдеры — менять осознанно (рантайм-компиляция).

## Архитектурные принципы (кратко; детали — ARCHITECTURE.md)
Resolution-independent GPU-рендер (SDF/MSDF, AA через `fwidth`) · backend-only · alloc-free hot path · GPU-first
(никакого CPU AA/glow/скруглений) · слоистый текст (Atlas/Metrics/Layout/Renderer) · фасад `Ui` с авто-fallback.

## Технологии
Fabric 1.21.1 (Yarn), Java 21, Gradle 9.5.1 + fabric-loom, LWJGL3 OpenGL, GLSL `#version 150` core-шейдеры,
MSDF (`msdf-atlas-gen` v1.4, офлайн), Gson, JUnit 5. Хост-JDK 25 (toolchain Java 21).

## Почему переход на новый рендер
Старый стек = `DrawContext.fill` квадами + bitmap-`TextRenderer`: пиксельный текст на зуме/4K, glow «набором
квадратов», грязные (полосящие) градиенты, ступенчатые скругления. Это потолок, не лечится косметикой. Новый стек —
MSDF-текст + аналитический SDF-фигуры, независимые от разрешения. Обоснование решений — [DECISIONS.md](DECISIONS.md).

## Что окончательно принято
См. **[DECISIONS.md](DECISIONS.md)** (журнал решений) и `docs/UI-V2.md` §10 (зафиксированные решения Stage 1).
