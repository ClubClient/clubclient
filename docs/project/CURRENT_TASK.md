# CURRENT_TASK — живой статус

> ЕДИНСТВЕННЫЙ постоянно обновляемый файл. Новый чат читает его первым, чтобы понять «где мы сейчас».
> Обновлять при каждом значимом шаге. Дата последнего обновления: **2026-06-28**.

## Current Stage
**Stage 2 / M2.1 Foundation — ЗАВЕРШЁН и в `main`.** Следующий — **M2.2 Core Widgets** (по процессу spec → review → plan → impl).

## Current Goal
**M2.2 implementation plan** (ветка `feat/ui-v2-m2.2-core`): после approve спеки виджетов — написать план реализации
(файлы, порядок, subagent-распределение по непересекающимся файлам, тесты, точки проверки) → approve → реализация.

## Current Task
**M2.2 Core Widgets Spec — APPROVED** (`docs/UI-V2-STAGE2-M2.2-SPEC.md`, 2026-06-28): публичный Widget API §3 заморожен.
Решения: Window = opt-in `ScrollArea` (только chrome); **G4** — добавлен токен `Accent.onAccent` (=ink0) для
Button PRIMARY-текста; capture auto-release — **не** в M2.2 (Stage-3 improvement); клавиатура — widget-scope
(`Space`/`Enter`, `Left`/`Right`). Self-review (5-мерный adversarial) — 0 critical, фиксы применены.
**G4 onAccent** (Accent/ClubDark/TokensTest + frozen-спека) — staged в дереве, коммитится как **infra перед виджетами**
(первый шаг реализации, после approve плана). **Кода виджетов нет и не пишется до approve плана.**

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
- **M2.2 infra-prep** (commit `23ff2c3`): **G1** pointer capture (`Component.mouseDragged` + `Container` `pressedChild`),
  **G2** `Interaction`-токены, **R13** disabled-scroll. TDD; `gradlew build` зелёный; 76 тестов 0 падений; arch-guard зелёный.
- **M2.2 Core Widgets Spec — APPROVED** (`docs/UI-V2-STAGE2-M2.2-SPEC.md`, 2026-06-28): 10 виджетов в `component/widget`,
  callback-типы, opt-in Window-scroll, G4 `onAccent`, keyboard widget-scope; self-review (5-мерный) 0 critical.

## In Progress
- **M2.2 implementation plan — пишется** (ветка `feat/ui-v2-m2.2-core`). Спека approved & committed. Виджетов нет.

## Next
1. **План M2.2** (этот шаг) → **approve**.
2. **Реализация M2.2** (после approve плана): первый infra-шаг — commit G4 `onAccent`; затем виджеты по TDD
   через subagents на непересекающихся файлах; главный агент — финальное ревью/интеграция.
3. **M2.3 Extended Widgets** — Dropdown/TextField/TabBar/Category/SearchBar/Tooltip/Badge/ProgressBar.
   Заложенный M2.3-пререквизит: overlay/portal-слой для поповеров/тултипов (escape ancestor-clip) — НЕ нужен для M2.2.
4. **`Ui.init()`-привязка** к клиентскому init — перенесена в Stage 3 (ClickGUI); `main` остаётся library-only.

## Blocked
— (ничего)

## Notes
- Перед стартом Stage 2 — спека по токенам/компонентам и approve (см. [AI_CONTEXT.md](AI_CONTEXT.md)).
- Stage 2 стартует с чистого `main`, без наследия PoC.
- Визуальный стиль (цвета/типографика) для компонентов — `docs/DESIGN.md` (но конкретные значения токенов решаются в спеке Stage 2).
