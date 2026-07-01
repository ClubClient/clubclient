# Club — Handoff (index)

> Документация разнесена по областям в `docs/`. Открой нужный файл перед правками.
> Пояснения — на русском, идентификаторы/код — как в проекте (English).

**Club** — клиентский utility-мод для Minecraft на Fabric 1.21.1 (Yarn, Java 21), пакет `com.club`.
Меню — на Right Shift, закрывается только ESC. Дизайн: премиальный, плоский, минималистичный
(без glass/blur/градиентов на тексте), мягкий сине-голубой акцент только на активных контролах.

## Документы

| Файл | Для чего |
|------|----------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Структура проекта, сборка/запуск, конфиг, миксины — начни отсюда |
| [docs/DESIGN.md](docs/DESIGN.md) | Дизайн-система: цвет, типографика, раскладка, контролы, чеклист ТЗ |
| [docs/ANIMATIONS.md](docs/ANIMATIONS.md) | Анимации рук (Pose/AnimationType) + визуальные твики вида |
| [docs/HUDS.md](docs/HUDS.md) | HUD (Armor/Potion/Target), их настройки и редактор позиций |
| [docs/UI-V2-MENU.md](docs/UI-V2-MENU.md) | **UI V2**: новый стек `com.club.ui`, меню «Variant D», HUD, виджеты, привязка к ClubConfig, dev-кейбинды (H/J/G) — для всех правок нового UI |

## Быстрый старт

```powershell
.\gradlew.bat compileJava   # проверка компиляции
.\gradlew.bat runClient     # дев-клиент
```

Эталон-борд (пример target-вида, не пиксель-в-пиксель): `ChatGPT Image 25 июн. 2026 г., 01_27_31.png`.
