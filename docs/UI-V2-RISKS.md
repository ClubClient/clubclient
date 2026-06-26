# Club — UI V2 Risk Ledger

> Сквозной реестр рисков нового рендер-стека, чтобы они не терялись между этапами. Обновляется по ходу
> Stage 1+. Severity: 🔴 high · 🟡 medium · 🟢 low. Status: open / mitigated / closed.

| # | Risk | Sev | Status | Where verified / mitigation |
|---|------|-----|--------|------------------------------|
| R1 | Рантайм-компиляция кастомных core-шейдеров в MC 1.21.1; кросс-GPU GLSL 150 (Intel/AMD/NVIDIA, Mesa) | 🔴 | open | T5 (регистрация), T10 (headless-приёмка), T12 (fallback при провале) |
| R2 | Размер MSDF-атласов и VRAM при полном charset (Latin+Latin-1+Cyrillic+symbols, ×3 веса) | 🟢 | measured | T3: 704²/712²/720² px, ~445–451KB/файл на диске, 448 глифов/вес; VRAM ≈ 6.1 MB суммарно (RGBA8, 3 веса) — приемлемо |
| R3 | Gradle 9.5.1 deprecations в `genMsdfAtlas` (`URL.withInputStream`, `copy{}`/`exec{}` в `doLast`) | 🟢 | mitigated | T3 (`bfd56e2`): переписано на чистый Java stdlib (URI/ZipFile/ProcessBuilder) + закрытие потоков + zip-slip guard + exit-code guard; свежий прогон OK, атласы байт-в-байт |
| R4 | Отсутствие атласа / неудачная компиляция шейдера → корректный fallback на LEGACY без краша | 🔴 | open | T12 (failure-path приёмка): missing atlas, shader fail, forced legacy, bad charset, init fail |
| R5 | `ColorTest.lerpMidpoint` слаб (маска `|0xFF000000` скрывает alpha-lerp) | 🟢 | open | триаж на финальном whole-branch review |
| R6 | `pushRoundedClip` в Stage 1 упрощён до rect-scissor (без настоящего скруглённого клипа) | 🟢 | accepted | API зафиксирован; полноценная реализация позже (Stage 2/3) |
| R7 | Сложность distance-field эффектов текста (outline/shadow/glow) в одном шейдере | 🟡 | open | T7 (ModernText), T10 (визуальная приёмка) |
| R8 | `line()` в Stage 1 только осепараллельный (диагонали аппроксимируются) | 🟢 | accepted | компоненты Stage 1 держат линии осепараллельными |
| R9 | Хост JDK 25 vs toolchain Java 21 (loom) | 🟢 | low | в PoC `runClient` отрабатывал; следим в T10 |
| R10 | Слабые GPU: лишние draw-call (один quad на фигуру + uniform-апдейты) | 🟢 | assessed-low | для UI-масштаба (десятки элементов/кадр) незначительно; MSDF требует `fwidth`/derivatives — core в GL 3.2 (MC-минимум), есть везде. Батчинг — опционально позже |
