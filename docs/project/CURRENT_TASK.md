# CURRENT_TASK — живой статус

> ЕДИНСТВЕННЫЙ постоянно обновляемый файл. Новый чат читает его первым, чтобы понять «где мы сейчас».
> Обновлять при каждом значимом шаге. Дата последнего обновления: **2026-06-26**.

## Current Stage
**Между Stage 1 и Stage 2.** Stage 1 завершён и в `main`. Stage 2 ещё не начат.

## Current Goal
Подготовить старт **Stage 2** (Tokens + библиотека компонентов) с чистого `main`.

## Current Task
Нет активной задачи реализации. Ожидается команда пользователя начать Stage 2 (тогда: спека токенов/компонентов →
approve → план → реализация).

## Completed
- **Stage 1 — рендер-фундамент `com.club.ui`** (merge `ce27dab`):
  контракты `UiRenderer`/`UiText`/`UiContext`/`Ui`; `ModernBackend`+`ModernText` (SDF/MSDF); `LegacyBackend`+`LegacyText`;
  core-шейдеры `ui_*`; MSDF-атласы Inter (Latin+Cyrillic) + `genMsdfAtlas`; 7 тестов (вкл. arch-guard).
  Рантайм-проверен; финальный 4-мерный review + фиксы применены; PoC/демо удалены; `gradlew build` зелёный.
- Документация проекта `docs/project/*` + ledgers `docs/UI-V2-RISKS.md` / `docs/UI-V2-PERF.md`.

## In Progress
— (пусто)

## Next
1. **Stage 2 — Tokens** (`com.club.ui.theme`: Radius/Spacing/Typography/Surface/Accent/Border/Shadow/Glow/Motion) —
   контракт в `docs/UI-V2.md` §6.
2. **Stage 2 — Components** (Button/Toggle/Slider/Dropdown/Checkbox/Card/Section/Window/Tab/TextField) — контракт §7–8.
3. При интеграции компонентов/ClickGUI добавить вызов **`Ui.init()`** (регистрация шейдеров) в клиентский init —
   сейчас фундамент library-only, точки входа нет.

## Blocked
— (ничего)

## Notes
- Перед стартом Stage 2 — спека по токенам/компонентам и approve (см. [AI_CONTEXT.md](AI_CONTEXT.md)).
- Stage 2 стартует с чистого `main`, без наследия PoC.
- Визуальный стиль (цвета/типографика) для компонентов — `docs/DESIGN.md` (но конкретные значения токенов решаются в спеке Stage 2).
