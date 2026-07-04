# Club — Handoff (index)

> Документация разнесена по областям в `docs/`. Открой нужный файл перед правками.
> Пояснения — на русском, идентификаторы/код — как в проекте (English).

**Club** — клиентский utility-мод для Minecraft на Fabric 1.21.1 (Yarn, Java 21), пакет `com.club`.
Меню — Right Shift (открыть/закрыть; ESC закрывает только поповер). Дизайн: премиальный, плоский,
минималистичный (без glass/blur/градиентов на тексте), бренд-акцент на интерактиве + приглушённые
категорийные цвета как идентичность (Stage 11).

> **ТЕКУЩАЯ ЗАДАЧА: [docs/STAGE23-TZ.md](docs/STAGE23-TZ.md)** — утверждённый владельцем полный
> список долгов (Stage 23–29: Target-данные, оффхенд-анимации, поповер/редактор на язык Stage 22,
> LEGACY-фоллбеки, клавиатура, тесты, гигиена). Читать первым.

## Документы

| Файл | Для чего |
|------|----------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Структура проекта, сборка/запуск, конфиг, миксины — начни отсюда |
| [docs/DESIGN.md](docs/DESIGN.md) | Дизайн-система: цвет, типографика, раскладка, контролы, чеклист ТЗ |
| [docs/ANIMATIONS.md](docs/ANIMATIONS.md) | Анимации рук (Pose/AnimationType) + визуальные твики вида |
| [docs/HUDS.md](docs/HUDS.md) | ЛЕГАСИ-справка по старому HUD (баннер внутри); актуальный HUD → HUD-LANGUAGE.md |
| [docs/UI-V2-MENU.md](docs/UI-V2-MENU.md) | **UI V2**: стек `com.club.ui`, боевое меню (Right Shift, Stage 6+), виджеты, привязка к ClubConfig — для всех правок нового UI |
| [docs/HUD-LANGUAGE.md](docs/HUD-LANGUAGE.md) | HUD-язык (Stage 13–19, амендменты сверху): chips, дуотон-иконки PixelIcons — **источник правды для правок HUD** |
| [docs/UI-V2-STAGE11-ICONS-SPEC.md](docs/UI-V2-STAGE11-ICONS-SPEC.md) | Stage 11 (завершена): иконки меню (MSDF-атлас), цвета категорий (palette A), карточки, лого; §5 — конвейер SDF-генератора (актуален) |

## Быстрый старт

```powershell
.\gradlew.bat compileJava   # проверка компиляции
.\gradlew.bat runClient     # дев-клиент
```

