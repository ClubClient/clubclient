# Club — Animations

> Всё для дальнейшей работы с **анимациями рук от первого лица** (кастомный удар) и связанными
> визуальными твиками вида.

## 1. Что это

Club заменяет ванильный свинг руки кастомной анимацией удара: для каждого кадра свинга считается
полный **Pose** (сдвиг + поворот рукояти), применяемый в том же пространстве, где машет ванилла.
Сочетание поворота с компенсирующим сдвигом — то, что не даёт клинку «проваливаться»/«орбитить».

## 2. Карта кода

| Файл | Роль |
|------|------|
| `modules/animations/AnimationType.java` | enum поз: каждая — функция `sample(swing, amp, arm) → Pose` |
| `modules/animations/Pose.java` | трансформ: `tx,ty,tz` (в 1/16 блока) + `rx,ry,rz` (градусы) |
| `modules/animations/AnimationModule.java` | stateless-хелпер: выбирает тип, отдаёт Pose/скорость, флаг override |
| `mixin/MixinHeldItemRenderer.java` | применяет руки + кастомную анимацию удара (вызывает `AnimationModule`) |
| `mixin/MixinLivingEntity.java` | масштабирует длительность свинга = скорость анимации |
| `config/ClubConfig.Animations` | сохраняемые настройки: `enabled`, `type`, `speed`, `amplitude` |
| GUI: вкладка **Combat → Animations** | dropdown типа + слайдеры Speed/Amplitude (`gui/ClubScreen.java`) |

## 3. Модель свинга

- `swing` ∈ [0..1] — прогресс ванильного свинга (0 = покой, 1 = конец). Pose обязан **чисто
  возвращаться в покой** при `swing == 0` и `swing == 1` (иначе рука «дёргается»).
- `arm` = +1 правая рука, −1 левая (sweeps зеркалятся).
- `amp` = amplitude (0.5 тонко … 1.5 резко), `Mth.clamp`-ится в `AnimationModule`.
- Хелперы кривых в `AnimationType`:
  - `front(s)` — фронт-загруженный «колокол» (быстро вылетает, плавно назад),
  - `bell(s)` — симметричный 0→1→0.

`AnimationModule.pose(out, swing, arm)` → Pose или `null` (покой/Vanilla/выключено).
`overridesVanillaSwing()` — true только если включено и тип ≠ `VANILLA`.
`speed()` — масштаб длительности свинга (0.5–2.0).

## 4. Текущие типы (AnimationType)

`VANILLA` (passthrough, без override) · `CLASSIC` (диагональный чоп, дефолт) · `THRUST` (укол) ·
`OVERHEAD` (замах + рубящий вниз) · `SHORT_SLASH` (широкий горизонтальный мах) ·
`SPIN` (полный оборот клинка плоско к экрану, чистый roll по Z).

## 5. Как добавить новую анимацию

1. Добавь константу в `AnimationType` с `label` и реализацией `sample`:
   ```java
   MY_MOVE("My Move") {
       @Override public Pose sample(Pose p, float s, float amp, int arm) {
           float e = front(s);
           return p.set(/* tx */ arm * -0.2f * e * amp, /* ty */ 0, /* tz */ -0.1f * e * amp,
                        /* rx */ -40f * e * amp, /* ry */ arm * 12f * e * amp, /* rz */ 0);
       }
   },
   ```
2. Убедись, что в крайних точках свинга поза = 0 (используй `front`/`bell`, умножай на них всё).
3. Зеркаль по `arm`, масштабируй по `amp`. Углы — в градусах, сдвиг — в 1/16 блока.
4. Больше ничего: dropdown в меню строится из `AnimationType.values()`, конфиг хранит `type` по `name()`.

## 6. Настройки (ClubConfig.Animations)

- `enabled` — мастер-тумблер (выкл → остаётся ванильный свинг).
- `type` — `AnimationType.name()` (фолбэк `CLASSIC` через `fromName`).
- `speed` 0.5–2.0 — через `MixinLivingEntity` меняет длительность свинга.
- `amplitude` 0.5–1.5 — масштаб амплитуды позы.

Меняются из меню (Combat → Animations) и сразу пишутся в `config/club_settings.json`.

## 7. Связанные визуальные твики вида (Visuals)

В том же слое рендера вида живут (см. `mixin/MixinGameRenderer.java`): **No Hurt Cam**
(убрать наклон от урона), **No Bobbing** (убрать покачивание), **Screen Stretch** (фейковый
aspect + чёрные полосы, `modules/screenstretch/*`, пресеты в `StretchPreset`), а также
**No Fire Overlay** (`mixin/MixinInGameOverlayRenderer.java`). Это не анимации удара, но тот же домен «вид».

Сборка/запуск — [ARCHITECTURE.md](ARCHITECTURE.md). Внешний вид меню — [DESIGN.md](DESIGN.md).
