# CURRENT_TASK — живой статус

> ЕДИНСТВЕННЫЙ постоянно обновляемый файл. Новый чат читает его первым, чтобы понять «где мы сейчас».
> Обновлять при каждом значимом шаге. Дата последнего обновления: **2026-06-28**.

## Current Stage
**Stage 2 / M2.1 Foundation — ЗАВЕРШЁН и в `main`.** Следующий — **M2.2 Core Widgets** (по процессу spec → review → plan → impl).

## Current Goal
**M2.2 infra-prep** (ветка `feat/ui-v2-m2.2-core`): реализовать утверждённые Foundation-доработки до виджетов —
**G1** pointer capture, **G2** `Interaction`-токены, **R13** disabled-scroll. Затем — спека API виджетов → approve.

## Current Task
Infra-Readiness audit проведён; пользователь **approve** (2026-06-28) на: **G1** — pointer capture как фундамент
(`Component.mouseDragged` + capture-диспатч release/drag в `Container`, маршрут только захваченному до release,
без broadcast/дублей); **G2** — маленький immutable `Interaction`-record (≤6 значений, ссылается на Palette/Accent,
не отдельная крупная подсистема); **R13** — `mouseScrolled` уважает `disabled` единообразно с прочим вводом.
Spec ([UI-V2-STAGE2-SPEC.md](../UI-V2-STAGE2-SPEC.md) §3.9/§5.1/§5.2/§5.5/§5.7/§10) обновлён. Реализация инфры —
по TDD. **Кода виджетов по-прежнему нет и не пишется до завершения infra-prep.**

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
- **M2.2 infra-prep — РЕАЛИЗОВАНО, ждёт approve** (ветка `feat/ui-v2-m2.2-core`): G1 capture + G2 `Interaction` + R13.
  TDD (RED→GREEN); `gradlew build` зелёный; **76 тестов 0 падений** (+10: CaptureTest 7, ContainerTest +2 R13,
  TokensTest +1); arch-guard зелёный. Виджетов нет. Не закоммичено.

## Next
1. **Завершить infra-prep** (G1/G2/R13) → тесты+arch-guard зелёные → infra-prep report → **approve**.
2. **Спека M2.2 Core Widgets** — уточнение API виджетов (Label/Divider/Panel/Card/Window/ScrollArea/Button/Toggle/
   Checkbox/Slider) поверх Foundation + gated dev-галерея → **approve**.
3. **План M2.2** → approve → реализация (subagent-driven).
4. **M2.3 Extended Widgets** — Dropdown/TextField/TabBar/Category/SearchBar/Tooltip/Badge/ProgressBar.
   Заложенный M2.3-пререквизит: overlay/portal-слой для поповеров/тултипов (escape ancestor-clip) — НЕ нужен для M2.2.
5. **`Ui.init()`-привязка** к клиентскому init — перенесена в Stage 3 (ClickGUI); `main` остаётся library-only.

## Blocked
— (ничего)

## Notes
- Перед стартом Stage 2 — спека по токенам/компонентам и approve (см. [AI_CONTEXT.md](AI_CONTEXT.md)).
- Stage 2 стартует с чистого `main`, без наследия PoC.
- Визуальный стиль (цвета/типографика) для компонентов — `docs/DESIGN.md` (но конкретные значения токенов решаются в спеке Stage 2).
