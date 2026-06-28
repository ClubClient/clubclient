# CURRENT_TASK — живой статус

> ЕДИНСТВЕННЫЙ постоянно обновляемый файл. Новый чат читает его первым, чтобы понять «где мы сейчас».
> Обновлять при каждом значимом шаге. Дата последнего обновления: **2026-06-28**.

## Current Stage
**Stage 2 / M2.1 Foundation — ЗАВЕРШЁН и в `main`.** Следующий — **M2.2 Core Widgets** (по процессу spec → review → plan → impl).

## Current Goal
Подготовить **спеку M2.2 Core Widgets** (уточнение API виджетов поверх Foundation) на ветке `feat/ui-v2-m2.2-core`, получить approve — затем план, затем код.

## Current Task
M2.1 смержён в `main` (Foundation: 66 тестов 0 падений; arch-guard + машинный `layout⊬theme`; keystone arch-review SOUND;
final review READY). Старт M2.2: сперва спека API core-виджетов. **Кода виджетов ещё нет.**

## Completed
- **Stage 1 — рендер-фундамент `com.club.ui`** (merge `ce27dab`):
  контракты `UiRenderer`/`UiText`/`UiContext`/`Ui`; `ModernBackend`+`ModernText` (SDF/MSDF); `LegacyBackend`+`LegacyText`;
  core-шейдеры `ui_*`; MSDF-атласы Inter (Latin+Cyrillic) + `genMsdfAtlas`; 7 тестов (вкл. arch-guard).
  Рантайм-проверен; финальный 4-мерный review + фиксы применены; PoC/демо удалены; `gradlew build` зелёный.
- Документация проекта `docs/project/*` + ledgers `docs/UI-V2-RISKS.md` / `docs/UI-V2-PERF.md`.
- **Stage 2 Spec** approved & frozen (`docs/UI-V2-STAGE2-SPEC.md`, 2026-06-28): полная токен-система
  (+Palette/Elevation), гибрид-layout (Column/Row/Stack/Spacer), motion-каркас, component/Container/FocusManager,
  core/extended компоненты, milestones M2.1–2.3, governing principles (Theme=единств. источник вида, DESIGN.md=канон).
- **Stage 2 / M2.1 Foundation** (ветка `feat/ui-v2-m2.1-foundation`, READY to merge): `theme/` (12 records + ClubDark + Tokens),
  `layout/` (value-types, Spacer, package-private Linear + Column/Row, Stack), `motion/` (Easing/Curves/Transition),
  `component/` (Component/Container/FocusManager/UiContextImpl). 66 тестов 0 падений; arch-guard + machine-checked layout⊬theme;
  keystone arch-review SOUND; final review READY. Без реальных виджетов (M2.2).

## In Progress
- **M2.2 Core Widgets — подготовка спеки** (ветка `feat/ui-v2-m2.2-core`). Кода нет.

## Next
1. **Спека M2.2 Core Widgets** — уточнение API виджетов (Label/Divider/Panel/Card/Window/ScrollArea/Button/Toggle/
   Checkbox/Slider) поверх Foundation + gated dev-галерея → **approve**.
2. **План M2.2** → approve → реализация (subagent-driven).
3. **M2.3 Extended Widgets** — Dropdown/TextField/TabBar/Category/SearchBar/Tooltip/Badge/ProgressBar.
4. **`Ui.init()`-привязка** к клиентскому init — перенесена в Stage 3 (ClickGUI); `main` остаётся library-only.
- Открытый вопрос M2.2: политика `mouseScrolled` на disabled-контейнере (R13) — решить с `ScrollArea`.

## Blocked
— (ничего)

## Notes
- Перед стартом Stage 2 — спека по токенам/компонентам и approve (см. [AI_CONTEXT.md](AI_CONTEXT.md)).
- Stage 2 стартует с чистого `main`, без наследия PoC.
- Визуальный стиль (цвета/типографика) для компонентов — `docs/DESIGN.md` (но конкретные значения токенов решаются в спеке Stage 2).
