# CURRENT_TASK — живой статус

> ЕДИНСТВЕННЫЙ постоянно обновляемый файл. Новый чат читает его первым, чтобы понять «где мы сейчас».
> Обновлять при каждом значимом шаге. Дата последнего обновления: **2026-06-28**.

## Current Stage
**Stage 2 — Design System (стартовал).** Stage 1 в `main`. Spec Stage 2 **approved & frozen** (2026-06-28).

## Current Goal
Реализовать **Stage 2** дизайн-систему (Tokens + layout + motion + компоненты) по `docs/UI-V2-STAGE2-SPEC.md`.

## Current Task
Spec Stage 2 заморожен. Пишется **план реализации** (writing-plans) → ревью плана пользователем → реализация
**M2.1 Foundation**. Кода компонентов ещё нет.

## Completed
- **Stage 1 — рендер-фундамент `com.club.ui`** (merge `ce27dab`):
  контракты `UiRenderer`/`UiText`/`UiContext`/`Ui`; `ModernBackend`+`ModernText` (SDF/MSDF); `LegacyBackend`+`LegacyText`;
  core-шейдеры `ui_*`; MSDF-атласы Inter (Latin+Cyrillic) + `genMsdfAtlas`; 7 тестов (вкл. arch-guard).
  Рантайм-проверен; финальный 4-мерный review + фиксы применены; PoC/демо удалены; `gradlew build` зелёный.
- Документация проекта `docs/project/*` + ledgers `docs/UI-V2-RISKS.md` / `docs/UI-V2-PERF.md`.
- **Stage 2 Spec** approved & frozen (`docs/UI-V2-STAGE2-SPEC.md`, 2026-06-28): полная токен-система
  (+Palette/Elevation), гибрид-layout (Column/Row/Stack/Spacer), motion-каркас, component/Container/FocusManager,
  core/extended компоненты, milestones M2.1–2.3, governing principles (Theme=единств. источник вида, DESIGN.md=канон).

## In Progress
- Stage 2 — план реализации (writing-plans).

## Next
1. **M2.1 Foundation** — `theme/` (все токены + ClubDark) · `layout/` · `motion/` · `component/` base (после approve плана).
2. **M2.2 Core Widgets** — Label/Divider/Panel/Card/Window/ScrollArea/Button/Toggle/Checkbox/Slider + gated dev-галерея.
3. **M2.3 Extended Widgets** — Dropdown/TextField/TabBar/Category/SearchBar/Tooltip/Badge/ProgressBar.
4. **`Ui.init()`-привязка** к клиентскому init — перенесена в Stage 3 (ClickGUI); `main` остаётся library-only.

## Blocked
— (ничего)

## Notes
- Перед стартом Stage 2 — спека по токенам/компонентам и approve (см. [AI_CONTEXT.md](AI_CONTEXT.md)).
- Stage 2 стартует с чистого `main`, без наследия PoC.
- Визуальный стиль (цвета/типографика) для компонентов — `docs/DESIGN.md` (но конкретные значения токенов решаются в спеке Stage 2).
