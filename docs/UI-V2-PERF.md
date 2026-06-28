# Club — UI V2 Performance Ledger

> Сквозная инженерная оценка производительности нового рендер-стека (до появления профилирования —
> честная оценка по коду). Обновляется с T6. Цель: не допустить регресса в «сотни draw call» и
> аллокаций в кадре.

## Stage 1 — ModernBackend (фигуры), снимок после T6

| Метрика | Значение (Stage 1) | План оптимизации |
|---|---|---|
| **Draw calls** | **1 на фигуру** (immediate-mode quad, `drawShapeQuad` → `BufferRenderer.drawWithGlobalProgram`) | батчер (Stage 2): параметры фигур → вершинные атрибуты, накопление, **1 draw / flush** |
| **Shader binds** | SDF-шейдер выставляется на каждый draw через **кэшированный** `sdfSupplier` (без аллокации лямбды) | при батчинге — 1 bind на flush |
| **Texture binds** | **0** для фигур (текстуры только у текста, T7) | — |
| **Аллокации в кадре (свой код)** | **нет** — кэшированный supplier, примитивные `float[]` стеки opacity/clip, `Color.*` int-математика, распакованный `Radii` | — |
| **Аллокации в кадре (неизбежные, MC)** | `Tessellator.begin()` / `BufferBuilder` / `.end()` на каждый immediate-draw — heap-churn пропорционально числу фигур | устраняется батчером (один персистентный буфер на flush) |
| **Clip** | GL-scissor, метрики окна закэшированы в `begin()` (нет `getInstance()` на каждую операцию); вложенные клипы пересекаются | rounded-clip — будущая шейдерная маска (TODO) |

**Узкие места (Stage 1):** при сотнях фигур за кадр — N draw-call + N MC-буфер-аллокаций. Для типичного
UI (десятки элементов) незначительно. Текст (T7) добавит свои quad’ы (по глифу) — там батчинг строки в один
буфер уже заложен.

**Дорожная карта оптимизации:**
1. **Батчер фигур** (Stage 2): перенести per-shape uniform’ы в вершинные атрибуты, копить quad’ы, flush
   одним draw на смену состояния → draws N→~1–2, и убирает per-shape MC-аллокацию. API `UiRenderer` не меняется
   (seam уже заложен в `drawShapeQuad`).
2. **Персистентный VBO** вместо `Tessellator` immediate-mode — снимает MC-аллокации в кадре.
3. Текст: батч глифов на строку (T7), при необходимости — атлас-батч на кадр.

> Принцип: контракт `UiRenderer` намеренно не зависит от способа сабмита → батчинг добавляется без
> переписывания компонентов.

## Text Performance Ledger (Stage 1 ModernText)

| Метрика | Значение (Stage 1) |
|---|---|
| **Glyph draw** | 1 quad/glyph, батчится в **1 draw/run** (одним `BufferBuilder` на строку → один `BufferRenderer.drawWithGlobalProgram`) |
| **Texture binds** | **1** на run (MSDF-атлас весá; смена atlas = смена bind, но внутри одного run — 0 смен) |
| **Shader binds** | **1** на run (`ui_msdf_text`, выставляется один раз перед loop по глифам) |
| **Atlas switches within a run** | **0** — все глифы строки берутся из одного атласа одного веса |

**Известные per-frame издержки (Stage 1):**

- `drawWrapped()` **re-wraps each frame**: внутри — `ArrayList`, `StringBuilder`, `String.split` на каждый
  вызов. Компоненты, показывающие статичный или редко меняющийся текст, **должны кэшировать wrapped lines**
  (пересчитывать только при изменении текста/ширины).
- `getUniform(String)` **lookup per draw**: `ShaderProgram.getUniform(String)` вызывается по имени на каждый
  глиф/run. Stage 2: кэшировать ссылки `GlUniform` при инициализации бэкенда.

**Исправленные находки:**

- `MsdfMetrics` autobox **FIXED**: lookup по char-ключу переведён на `int`-keyed структуру — автобокс
  `Character` на каждый глиф устранён.
