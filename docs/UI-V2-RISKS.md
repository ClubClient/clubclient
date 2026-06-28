# Club — UI V2 Risk Ledger

> Сквозной реестр рисков нового рендер-стека, чтобы они не терялись между этапами. Обновляется по ходу
> Stage 1+. Severity: 🔴 high · 🟡 medium · 🟢 low. Status: open / mitigated / closed.

| # | Risk | Sev | Status | Where verified / mitigation |
|---|------|-----|--------|------------------------------|
| R1 | Рантайм-компиляция кастомных core-шейдеров в MC 1.21.1; кросс-GPU GLSL 150 (Intel/AMD/NVIDIA, Mesa) | 🟢 | closed | T10: `ui_sdf_shape`/`ui_msdf_text` скомпилировались в рантайме, MSDF/SDF рендерятся @1×+500× без ошибок (на тест-GPU). Кросс-GPU — мониторим при реальной раздаче |
| R2 | Размер MSDF-атласов и VRAM при полном charset (Latin+Latin-1+Cyrillic+symbols, ×3 веса) | 🟢 | measured | T3: 704²/712²/720² px, ~445–451KB/файл на диске, 448 глифов/вес; VRAM ≈ 6.1 MB суммарно (RGBA8, 3 веса) — приемлемо |
| R3 | Gradle 9.5.1 deprecations в `genMsdfAtlas` (`URL.withInputStream`, `copy{}`/`exec{}` в `doLast`) | 🟢 | mitigated | T3 (`bfd56e2`): переписано на чистый Java stdlib (URI/ZipFile/ProcessBuilder) + закрытие потоков + zip-slip guard + exit-code guard; свежий прогон OK, атласы байт-в-байт |
| R4 | Отсутствие атласа / неудачная компиляция шейдера → корректный fallback на LEGACY без краша | 🟢 | closed | T12: симулированный missing atlas → `ModernText` ловит → `modernAvailable()=false` → весь UI на LEGACY, без краша (лог + скрин `uiv2_fallback_no_atlas`); forced LEGACY работает; missing glyph → '?' (FallbackLogicTest) |
| R5 | `ColorTest.lerpMidpoint` слаб (маска `|0xFF000000` скрывает alpha-lerp) | 🟢 | closed | fixed in tests: alpha-channel assertion добавлен, маска убрана — lerp корректно проверяет все 4 канала ARGB |
| R6 | `pushRoundedClip` в Stage 1 упрощён до rect-scissor (без настоящего скруглённого клипа) | 🟢 | accepted | API зафиксирован; полноценная реализация позже (Stage 2/3) |
| R7 | Сложность distance-field эффектов текста (outline/shadow/glow) в одном шейдере | 🟢 | closed | T10: outline/shadow/glow рендерятся корректно (скрин `uiv2_modern`) |
| R8 | `line()` в Stage 1 только осепараллельный (диагонали аппроксимируются) | 🟢 | accepted | компоненты Stage 1 держат линии осепараллельными |
| R9 | Хост JDK 25 vs toolchain Java 21 (loom) | 🟢 | low | в PoC `runClient` отрабатывал; следим в T10 |
| R10 | Слабые GPU: лишние draw-call (один quad на фигуру + uniform-апдейты) | 🟢 | assessed-low | для UI-масштаба (десятки элементов/кадр) незначительно; MSDF требует `fwidth`/derivatives — core в GL 3.2 (MC-минимум), есть везде. Батчинг — опционально позже |
| R11 | Arch-guard `.fill(` ложно срабатывал на layout-API `Sizing.fill()/Spacer.fill()` | 🟢 | closed | M2.1 (`f4dd518`, user-approved): strip-list расширен (как для `Arrays.fill`); реальный `DrawContext.fill(` по-прежнему ловится. **Урок:** diff-scoped per-task ревью не видят кросс-tree arch-регресс → гонять полный arch-test на гейте |
| R12 | `Linear.measure()` для Fill/Weight отдаёт intrinsic-размер (не allocated) — недо-счёт при size-to-content родителе | 🟢 | pinned | M2.1: семантика осознанно закреплена golden-тестом `measureReportsIntrinsicSizeIgnoringFlex` + doc; уточнение позже — аддитивно, без смены контракта |
| R13 | `Container.mouseScrolled` не проверяет `enabled` (в отличие от `mouseClicked`) | 🟢 | deferred | политика scroll-over-disabled решается вместе с `ScrollArea` в M2.2 (scrollable-виджетов в M2.1 нет) |
