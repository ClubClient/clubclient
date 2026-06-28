# FILE_STRUCTURE — дерево и ответственность

> Где что лежит. Существующий клиент (`gui/hud/modules/mixin/...`) — детально в `docs/ARCHITECTURE.md` (не трогаем до
> Stage 5). Здесь — фокус на новом стеке `com.club.ui` + что добавится дальше.

## Новый рендер-стек (Stage 1, готово)
```
src/main/java/com/club/ui/
├── Ui.java                  фасад: renderer()/text()/backend()/setBackend()/modernAvailable()/init()/beginFrame()
├── UiRenderer.java          контракт фигур/теней/свечения/клиппинга/opacity
├── UiText.java              контракт текста/метрик/переноса
├── UiContext.java           контекст компонента: renderer()/text()/time()
├── Color.java Axis.java Radii.java        чистые value-типы (без MC)
├── text/                    ЧИСТАЯ логика (без Minecraft/GL), юнит-тестируемая
│   ├── Weight.java Align.java
│   ├── TextStyle.java TextEffect.java      стиль + эффекты (None/Outline/Shadow/Glow)
│   ├── Charset.java                        frozen codepoint set (Latin+Cyrillic+symbols)
│   ├── MsdfMetrics.java                    Metrics: парсер/модель JSON атласа (int-keyed, alloc-free get)
│   ├── GlyphSource.java GlyphSink.java ResolvedGlyph.java   seam Layout↔Atlas (out-param)
│   └── TextLayout.java                     Layout: codepoint-aware раскладка/перенос/выравнивание
└── backend/                 ЕДИНСТВЕННЫЙ слой с GL (backend-only rule)
    ├── UiShaders.java                      регистрация core-шейдеров (CoreShaderRegistrationCallback)
    ├── Backends.java                       держатель инстансов + per-frame begin
    ├── ModernBackend.java                  UiRenderer на SDF-шейдере (alloc-free, clip-stack, fail-safe)
    ├── ModernText.java                     UiText на MSDF (GlyphBatch: 1 draw/run; эффекты; fail-safe)
    ├── MsdfAtlas.java                      Atlas: текстура+метрики одного веса
    ├── FontRegistry.java                   Registry: weight→atlas, resolve (glyph-cache seam)
    ├── LegacyBackend.java                  fallback UiRenderer (DrawContext.fill)
    └── LegacyText.java                     fallback UiText (vanilla TextRenderer)

src/main/resources/assets/club/
├── shaders/core/ui_sdf_shape.{json,vsh,fsh}   аналитический SDF (per-corner radius, fill/border/glow/gradient)
├── shaders/core/ui_msdf_text.{json,vsh,fsh}   MSDF (median + screen-px AA, outline/glow)
└── ui/font/msdf/inter_{regular,medium,semibold}.{png,json}   замороженные MSDF-атласы

src/test/java/com/club/ui/
├── ColorTest · MsdfMetricsTest · TextLayoutTest · CharsetCoverageTest · FallbackLogicTest   (чистая логика)
├── ArchitectureRuleTest   (machine-enforced backend-only rule)
└── SanityTest

build.gradle   задача genMsdfAtlas (офлайн-генерация атласов; pinned msdf-atlas-gen v1.4)
```

## Документация
```
docs/project/   ← долговременная память (этот набор; читать первым)
docs/UI-V2.md            канон контрактов (UiRenderer/UiText/Tokens/Components) + зафикс. решения
docs/UI-V2-STAGE1-PLAN.md  запись плана Stage 1 (исторический)
docs/UI-V2-RISKS.md      risk ledger (живой)
docs/UI-V2-PERF.md       performance ledger (живой)
docs/{ARCHITECTURE,DESIGN,HUDS,ANIMATIONS}.md   существующий клиент (старый стек, до Stage 5)
docs/POC-RENDER-STACK.md  исторический: анализ/доказательство PoC
```

## Будет добавлено на следующих Stage
- **Stage 2:** `com.club.ui/theme/` (`Tokens`, `Theme`, категории) · `com.club.ui/component/` (`Component`, Button,
  Toggle, Slider, Dropdown, Checkbox, Card, Section, Window, Tab, TextField) · `com.club.ui/layout/` (раскладка) ·
  каркас Motion. Точка входа клиента, вызывающая `Ui.init()`.
- **Stage 3:** новый ClickGUI-экран(ы) на компонентах.
- **Stage 4:** новые HUD на `Ui`.
- **Stage 5:** удаление старых `gui/*`,`hud/*`,`Theme`,`ClubFont`,`RenderHelper`.

> Не трогаем до Stage 5: `com.club.gui.*` (вкл. `gui/sandbox`), `com.club.hud.*`, `com.club.util.{ClubFont,RenderHelper}`,
> `com.club.gui.Theme`, миксины. Это существующий клиент.
