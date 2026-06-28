# ARCHITECTURE — UI V2 рендер-стек

> Карта слоёв нового стека `com.club.ui`, их ответственности и правила зависимостей. **Точные сигнатуры/код
> контрактов — канон в `docs/UI-V2.md` §3–7** (не дублируется здесь). Архитектура существующего клиента (сборка,
> миксины, пакеты `gui/hud/modules`) — в `docs/ARCHITECTURE.md`.

## Слои (сверху вниз)

```
Components / Layouts / Tokens   ← Stage 2+ (ещё нет); рисуют ТОЛЬКО через UiContext
        │  (зависит вниз)
        ▼
Ui  (фасад)                     ← выбор бэкенда, авто-fallback, beginFrame, init
   ├── UiContext                ← то, что получает компонент в render(): renderer()/text()/time()
   ├── UiRenderer (контракт)
   └── UiText     (контракт)
        │
        ▼
backend/  (ЕДИНСТВЕННЫЙ слой с низкоуровневым GL)
   ├── ModernBackend : UiRenderer     (аналитический SDF-шейдер)
   ├── ModernText    : UiText          (MSDF; внутри: Atlas→Metrics→Layout→Renderer)
   ├── LegacyBackend : UiRenderer     (DrawContext.fill fallback)
   ├── LegacyText    : UiText          (vanilla TextRenderer fallback)
   ├── UiShaders / Backends           (регистрация шейдеров; держатели инстансов)
   ├── MsdfAtlas (Atlas) / FontRegistry (Registry, GlyphSource)
        │
        ▼
text/  (ЧИСТАЯ логика, без Minecraft/GL)
   ├── MsdfMetrics (Metrics)          ← парсер/модель JSON атласа
   ├── TextLayout + GlyphSink + GlyphSource + ResolvedGlyph (Layout)
   ├── TextStyle / TextEffect / Weight / Align / Charset
core value-types: Color / Axis / Radii   (чистые, без MC)
```

## Ответственность и границы знания

| Слой | Что делает | Что ЗНАЕТ | Чего знать НЕ должен |
|---|---|---|---|
| **Components/Layouts/Tokens** (Stage 2+) | UI-элементы, раскладка, дизайн-значения | `UiContext`, `Ui*` контракты, токены | бэкенды, GL, `DrawContext`, какой бэкенд активен |
| **Ui (фасад)** | выбрать активный бэкенд, авто-fallback, `init()` (регистрация шейдеров), `beginFrame()` | оба бэкенда, `UiShaders.ready()`, здоровье `Modern*` | детали отрисовки |
| **UiRenderer/UiText/UiContext** | контракты рендера (фигуры/текст/контекст) | только value-типы (`Color/Axis/Radii/TextStyle/...`) | Minecraft/GL (MC-free; `Ui` — единственный, кто принимает `DrawContext`) |
| **backend/Modern*** | реализация контрактов на GPU (SDF/MSDF), clip, opacity, эффекты, fail-safe | LWJGL GL, `RenderSystem`, шейдеры, атласы | компоненты, токены |
| **backend/Legacy*** | best-effort fallback на `DrawContext`/vanilla-шрифт | `DrawContext` | — |
| **backend/MsdfAtlas/FontRegistry** | загрузка атлас-текстур; резолв codepoint→(atlasId, glyph); glyph-cache seam | MC ресурсы/текстуры | layout, рендер |
| **text/ (Metrics/Layout)** | метрики, раскладка глифов (codepoint-aware), перенос, выравнивание | только `text/`-типы | **Minecraft/GL (полностью чистый слой)** |
| **value-types** | `Color`/`Axis`/`Radii` | ничего | всё остальное |

## Правила зависимостей
- Зависимости только **вниз** по схеме. `text/` и value-типы — **без `net.minecraft`** (чистые, юнит-тестируемые).
- Компоненты ↔ рендер только через `UiContext` (`renderer()`/`text()`). Никаких прямых бэкендов/`DrawContext`.
- `text/` (Layout) отделён от `backend/` (Atlas/Renderer) через `GlyphSource`/`GlyphSink` (out-param, без аллокаций) —
  поэтому Layout можно заменить (bidi/RTL/shaping) без переписывания рендера.
- Текстуры атласов опаковы для Layout: оперирует `atlasId` (int), `backend` маппит его в GL-текстуру.

## Backend-only rendering (hard rule)
Любой `RenderSystem`/`BufferBuilder`/`Tessellator`/`BufferRenderer`/`GlUniform`/`ShaderProgram`/`DrawContext.fill`/
`.drawText(`/`ClubFont`/`RenderHelper` — **только внутри `com.club.ui.backend.*`**. Машинно проверяется
`ArchitectureRuleTest` (скан `src/main/java/com/club/ui`, исключая `backend/`). Цель: компоненты/ClickGUI/HUD никогда
не обходят стек напрямую. См. [DECISIONS.md](DECISIONS.md) D-03.

## Запреты в ModernBackend/ModernText
Нет CPU-реализаций AA/glow/скруглений/градиентов; всё из шейдера. Невозможное сейчас — `// TODO`, не временный CPU.
Нет аллокаций в render-хотпате из своего кода (кэш-supplier, примитивные стеки, reusable batch/scratch; единственное
неизбежное — MC `Tessellator`/`BufferBuilder` на draw). Детали — [PERFORMANCE.md](PERFORMANCE.md).

## Resilience
`ModernText` и `ModernBackend` ловят рантайм-исключения (атлас/GL) → `healthy()=false` → `Ui.modernAvailable()=false`
→ весь UI авто-переключается на LEGACY без краша. `Ui.setBackend()` — ручной override (отладка).

## Будущее (Stage 2+)
- **Tokens** (`com.club.ui.theme`): Radius/Spacing/Typography/Surface/Accent/Border/Shadow/Glow/Motion — контракт `docs/UI-V2.md` §6.
- **Components**: базовый `Component` (measure/layout/render/input/state/events) + `UiContext` — §7–8.
- **Layouts**: раскладка контейнеров.
- Шейдерный **батчер** фигур/текста (seam уже в `drawShapeQuad`/`GlyphBatch`) — без смены API. См. PERFORMANCE.md.
