# PoC — New UI Render Stack (Current vs New)

> Изолированный proof-of-concept для визуального сравнения текущего рендера UI и нового
> (MSDF-текст + SDF-фигуры/glow/gradient). НЕ часть клиента, ничего из боевого GUI не трогает.
> Пояснения — на русском, идентификаторы/код — как в проекте (English).

## 1. Цель

Получить честное визуальное A/B: текущий стек (`util/RenderHelper` + `util/ClubFont` через
`DrawContext.fill` и bitmap-TextRenderer) против нового (custom core-шейдеры: тру-MSDF текст и
аналитический SDF для фигур/скруглений/glow/градиентов). Решение о полной миграции принимается
ТОЛЬКО после визуального подтверждения качества.

Корень проблемы (зафиксирован при анализе): весь UI рисуется CPU-растром квадами через
`DrawContext.fill()` + bitmap-glyph-атлас MC. Отсюда «пиксельный» текст на зуме, glow «набором
квадратов», грязные (полосящие) градиенты, рассыпание интерфейса при приближении.

## 2. Жёсткие ограничения

- НЕ модифицируем: `gui/ClubScreen`, `gui/Theme`, `gui/components/*`, `hud/*`, `util/ClubFont`,
  `util/RenderHelper`, `ClubClient`, `ClubMod`, миксины, существующий `gui/sandbox/*`,
  `assets/club/font/club_*.json`.
- НЕ меняем цвета/размеры/тени/позиции — PoC лишь воспроизводит ТЕКУЩИЙ вид двумя рендерами.
- Не переносим PoC в клиент: код активен только при env `CLUB_POC=1`.
- Единственная правка существующего файла — **additive** client-entrypoint в `fabric.mod.json`
  (`com.club.poc.PocBootstrap`), который без `CLUB_POC=1` не делает ничего.

## 3. Состав PoC (всё новое, изолировано)

```
com.club.poc
├── PocBootstrap              — gated (CLUB_POC=1) headless-захват: открыть экран, снять PNG, стоп
├── RenderCompareScreen       — вертикальный сплит CURRENT | NEW; 6 элементов в каждом столбце
├── render/
│   ├── PocShaders            — регистрация core-шейдеров (CoreShaderRegistrationCallback)
│   ├── PocRenderer           — фасад фигур: roundedRect/border/glow/gradient (через SDF-шейдер)
│   ├── MsdfFont              — загрузка атласа+метрик, раскладка строки, отрисовка глифов
│   └── PocText               — роли (cat/tab/name/hud/list/desc/small), зеркало ClubFont API
└── (samples)                 — фикс. данные Armor/Target HUD для обоих столбцов

assets/club/poc/
├── shaders/core/club_sdf_shape.{json,vsh,fsh}
├── shaders/core/club_msdf_text.{json,vsh,fsh}
└── msdf/inter_{regular,medium,semibold}.{png,json}   ← сгенерированы офлайн
```

## 4. Новый стек — техника

- **SDF-фигуры** (`club_sdf_shape`): один quad на фигуру; во фрагменте — аналитическая дистанция
  до rounded-box (`sdRoundBox`) из локальных координат + uniforms (halfSize, radius, mode, softness,
  2 цвета градиента). Режимы: fill (AA через `smoothstep(fwidth)`), 1px border (полоса дистанции),
  glow (непрерывный falloff), gradient (интерполяция в шейдере + ordered-dither против банбинга).
  ~десяток draw-call на экран — для PoC незаметно.
- **MSDF-текст** (`club_msdf_text`): атлас-текстура, `median(r,g,b)`, AA через `fwidth`, `pxRange`
  из atlas.distanceRange. Все глифы строки — в одном буфере. Soft-shadow HUD = второй проход с
  тем же визуальным результатом (бледная подложка +1px), без изменения смысла тени.
- Атлас MSDF генерится офлайн официальным **msdf-atlas-gen v1.4** (тру-MSDF). Рантайм-нативов нет —
  грузим только PNG + JSON (Gson). Задача `genMsdfAtlas` скачивает пинованный бинарник в
  `build/tools/` и рендерит три веса.

  > **Значения PoC-эпохи (`-size 48`, charset = printable ASCII + `…`) устарели.** Боевой атлас,
  > который лежит в `assets/club/ui/font/msdf/`, собран с `-type msdf -size 40 -pxrange 6` и полным
  > charset (Latin + Latin-1 + Cyrillic + пунктуация/символы); это то, что реально записано в
  > `inter_*.json` (`atlas.size = 40`, `distanceRange = 6`). Числа выше — исторические, не сверяйте
  > по ним.

  > **Воспроизводимость — с оговоркой.** Задача `genMsdfAtlas` брала шрифты из
  > `src/main/resources/assets/club/font/`, откуда они уехали в `tools/fonts/` ещё в `70a44f0`; путь
  > не обновили, и запуск падал с «Failed to load specified font file». Путь **починен**, но
  > **прогон с момента переезда шрифтов не выполнялся**, поэтому байт-в-байт совпадение с
  > закоммиченным атласом сейчас — ожидание, а не измерение. Задача **только для Windows** (тянет
  > win64-сборку msdf-atlas-gen). Подробности — `docs/UI-V2-RISKS.md`, R3.

## 5. Экран сравнения

Вертикальный сплит, по центру разделитель + подписи `CURRENT` / `NEW`. В каждом столбце один и тот
же layout-код, отличаются только вызовы рендера:
заголовок · Toggle · Slider · карточка · пример Armor HUD · пример Target HUD. Значения HUD —
фиксированные (без привязки к live-config; боевые HUD-файлы не трогаем).

## 6. Скриншоты (headless, как существующий SandboxBootstrap)

`PocBootstrap` при `CLUB_POC=1` сохраняет в `run/screenshots/`:
1. **A/B 1×** — сплит в обычном масштабе.
2. **A/B close-up** — тот же сплит с matrix-zoom (≈3×): current «рассыпается», MSDF/SDF остаётся резким.
3. **Real game scale** — оба HUD’а поверх реального кадра игры в нативном GUI-масштабе. Попытка
   загрузить дев-мир (`saves/Новый мир`); фолбэк — оверлей поверх title-панорамы (тоже реальный
   игровой фреймбуфер и реальный масштаб).

## 7. Риски

- Интеграция core-шейдеров в 1.21.1 (vertex format/attributes, blend, premultiplied alpha,
  sRGB/gamma для AA текста).
- Headless-вход в мир для кадра №3 (есть фолбэк на панораму).
- Хост JDK 25 vs toolchain Java 21 (loom). `build/` существует — вероятно ок; иначе нужен JDK 21.

## 8. Критерий успеха

На close-up кадре текст/скругления/glow/градиент справа (NEW) — чистые и резкие; слева (CURRENT)
видна пикселизация/квадраты/банбинг. На real-scale кадре NEW читается не хуже CURRENT. Только после
этого — решение о полной миграции (отдельная спека/план).
