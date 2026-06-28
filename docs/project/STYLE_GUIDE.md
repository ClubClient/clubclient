# STYLE_GUIDE — правила кода

> Соглашения для нового стека `com.club.ui` (и нового кода вообще). Существующий клиент следует своим паттернам
> (`docs/ARCHITECTURE.md`) — его не трогаем до Stage 5.

## Naming
- Идентификаторы/типы/методы — **English**. UI-лейблы — English. Пояснительные комментарии/доки — RU (как в проекте).
- Имена по сути, а не по реализации: `roundedRect`, `GlyphSource`, `modernAvailable` (что делает), не `doFillLoop`.
- Классы — `PascalCase`, методы/поля — `camelCase`, константы — `UPPER_SNAKE`. Пакеты — `lowercase`.

## Java conventions (Java 21)
- `options.release = 21`. Использовать современные конструкции: `record`, `sealed interface`, pattern-matching
  `instanceof`, switch-expr — где уместно (как в `TextEffect`, `Radii`).
- Иммутабельность по умолчанию: value-типы — `final` + `final` поля (`Color`, `Radii`, `TextStyle`); withers вместо
  сеттеров. Исключение — намеренно mutable reusable holders на хотпате (`ResolvedGlyph`, `GlyphBatch`) — с явным
  комментарием почему.
- Цвет — упакованный `int` ARGB; никаких `java.awt.Color`/боксинга на хотпате.
- Координаты/размеры — `float`, px.

## Package rules
- Чистая логика (`com.club.ui`, `com.club.ui.text`, value-типы) — **без `import net.minecraft.*`**.
- Низкоуровневый GL/`DrawContext`/`RenderSystem`/... — **только `com.club.ui.backend.*`** (backend-only; enforced
  `ArchitectureRuleTest`). Любой такой вызов вне backend — баг.
- Зависимости только вниз по слоям (см. [ARCHITECTURE.md](ARCHITECTURE.md)).

## Документация и комментарии
- Каждый публичный тип/контракт — короткий Javadoc: что делает, как использовать, от чего зависит.
- Комментарии объясняют **почему**, а не пересказывают код. Помечать архитектурные швы: `// BATCHING SEAM`,
  `// GLYPH-CACHE SEAM`.
- `// TODO` — отложенная, но запланированная работа (напр. rounded-clip как шейдерная маска). `// FIXME` — известный
  дефект/край (напр. silent overflow), который должен всплыть при аудите. Не оставлять немых заглушек.
- Никаких временных «костылей» в постоянном коде. Dev-only (bootstrap/флаги/showcase) — отдельно и удаляется перед merge.

## Производительность (хотпат)
- Render-методы — **allocation-free** из своего кода: кэшировать suppliers, использовать примитивные стеки и reusable
  буферы, не создавать `Color`/`List`/`Radii`/лямбды на кадр. Неизбежные MC-аллокации — документировать в
  `docs/UI-V2-PERF.md`. Подробно — [PERFORMANCE.md](PERFORMANCE.md).

## Тесты (TDD где применимо)
- Чистую логику (цвет, метрики, layout, charset, fallback) покрывать **JUnit 5**; тесты проверяют РЕАЛЬНОЕ поведение,
  не тавтологии. Для логики — RED→GREEN.
- GL/рендер не юнит-тестируется → проверяется компиляцией + headless-приёмкой (скриншоты) + визуальным осмотром.
- `ArchitectureRuleTest` (backend-only) держать зелёным — это инвариант, а не обычный тест.
- Output тестов — чистый (без варнингов/шума).

## Code Review
- Ревью по диффу: spec-compliance (ничего лишнего/недостающего) + качество (чисто, тестируемо, без аллокаций/CPU-эффектов).
- На финале этапа — adversarial whole-branch review по измерениям (correctness/perf/architecture/tests).
- Critical/Important находки чинятся до merge; Minor — в ledger/триаж.

## Git
- Маленькие атомарные коммиты по смыслу; сообщения `type(scope): summary` (`feat(ui)`, `fix(ui)`, `test(ui)`,
  `docs(ui)`, `chore(ui)`). Трейлер `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Не коммитить `build/`, `run/`, `.gradle/` (gitignored). Сгенерированные атласы (`assets/club/ui/font/msdf/*`) —
  коммитятся (frozen artifacts).
