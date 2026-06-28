# ROADMAP — UI V2

> Пять этапов миграции UI. Один Stage за раз, по циклу spec→approve→plan→impl→review→milestone. Порядок неизменен
> (charter V2). Контракты будущих этапов уже зафиксированы в `docs/UI-V2.md`.

## Stage 1 — Рендер-фундамент ✅ DONE (merge `ce27dab`)
- **Цель:** заменить потолочный рендер новым resolution-independent стеком `com.club.ui`.
- **Результат:** контракты + `Modern*`/`Legacy*` бэкенды + MSDF/SDF шейдеры + атласы + тесты; рантайм-проверен; в `main`.
- **Входило:** `UiRenderer`/`UiText`/`UiContext`/`Ui`; `ModernBackend`/`ModernText`/`LegacyBackend`/`LegacyText`;
  `ui_*` шейдеры; `genMsdfAtlas` + атласы (Latin+Cyrillic); JUnit + arch-guard; ledgers; docs.
- **НЕ входило:** токены, компоненты, ClickGUI, HUD, любой визуальный стиль, in-client точка входа.
- **Критерии (выполнены):** контракты зафиксированы; backend-only enforced тестом; рантайм: шейдеры компилируются,
  текст резкий @1×/500×, graceful fallback без краша; `gradlew build` зелёный; existing gui/hud не тронуты.

## Stage 2 — Дизайн-система: Tokens + Components ⬅ IN PROGRESS (M2.1 ✅ DONE)
- **Цель:** типизированные токены + библиотека компонентов поверх контрактов Stage 1.
- **Результат:** `com.club.ui.theme.Tokens` + базовый `Component`/`UiContext`-цикл + минимум компонентов, покрытые тестами/приёмкой.
- **Входит:** Tokens (Radius/Spacing/Typography/Surface/Accent/Border/Shadow/Glow/Motion — `docs/UI-V2.md` §6);
  компоненты Button/Toggle/Slider/Dropdown/Checkbox/Card/Section/Window/Tab/TextField (§7–8); каркас анимаций (Motion);
  **подключение `Ui.init()`** в клиентский init (регистрация шейдеров — сделать здесь или в Stage 3).
- **НЕ входит:** конкретный ClickGUI-экран, HUD, перенос старого функционала, ретайр legacy.
- **Критерии готовности:** компоненты рисуют только через `UiContext`; ноль литералов цвета/размера в компонентах
  (только токены); ноль аллокаций в render (UiContext/стили — в полях/константах); arch-guard зелёный; приёмка вида.
- **Прогресс:** **M2.1 Foundation ✅ DONE** (в `main`): полная токен-система (+Palette/Elevation) + ClubDark; гибрид-layout
  Column/Row/Stack/Spacer над package-private `Linear`; motion Easing/Curves/Transition; Component/Container/FocusManager/
  UiContextImpl. 66 тестов 0 падений; arch-guard + машинный `layout⊬theme`; keystone arch-review SOUND; final review READY.
  Спека `docs/UI-V2-STAGE2-SPEC.md`, план `docs/UI-V2-STAGE2-M2.1-PLAN.md`. Реальных виджетов нет.
  **Next — M2.2 Core Widgets** (Label/Divider/Panel/Card/Window/ScrollArea/Button/Toggle/Checkbox/Slider) → **M2.3 Extended**.

## Stage 3 — Новый ClickGUI
- **Цель:** собрать клиентское меню/ClickGUI из компонентов Stage 2.
- **Результат:** работающий ClickGUI на новом стеке (отдельный от старого `ClubScreen`).
- **Входит:** экраны, навигация, контейнеры, ввод; визуальный язык по `docs/DESIGN.md`.
- **НЕ входит:** замена/удаление старого меню (это Stage 5), HUD.
- **Критерии:** ClickGUI полностью на `Ui`/компонентах; премиальное качество в рантайме; старый UI ещё нетронут.

## Stage 4 — Новые HUD
- **Цель:** HUD-элементы (Armor/Target/Potion и др.) на новом стеке.
- **Результат:** новые HUD-рендеры через `Ui`, готовые заменить старые `hud/*`.
- **Входит:** HUD-компоненты, редактор позиций при необходимости.
- **НЕ входит:** удаление старых `hud/*` (Stage 5).
- **Критерии:** HUD читаемы в реальном масштабе игры; на новом стеке; старые HUD ещё работают параллельно.

## Stage 5 — Перенос функционала + ретайр legacy
- **Цель:** перевести существующий функционал на новый UI и удалить старый стек.
- **Результат:** единый UI на `com.club.ui`; `Theme`/`ClubFont`/`RenderHelper`/старые `gui/*`,`hud/*` удалены; `Legacy*`
  остаётся только как fallback-бэкенд.
- **Входит:** миграция меню/HUD/настроек; удаление дублирующего старого кода.
- **НЕ входит:** новые фичи вне UI.
- **Критерии:** старый рендер-стек удалён; всё работает на новом; тесты/приёмка зелёные.

> До Stage 5 существующие `gui/*`, `hud/*`, `Theme`, `ClubFont`, `RenderHelper` **не трогаются**.
