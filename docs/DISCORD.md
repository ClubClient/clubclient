# Discord — структура, тексты, ассеты

> Сервер: `discord.gg/kq2DYuTQnW` (guild `1526569728850661466`). Инвайт вечный — проверено.
>
> Правило этого документа то же, что у мода: **ничего лишнего**. Шесть каналов, три роли, ноль
> декоративных полок. Пустая категория «под будущее» кричит «заброшено» громче, чем её отсутствие.

---

## 1. Ассеты (собираются из репозитория, не рисуются руками)

```bash
./gradlew genDiscordBrand    # build/discord/icon.png (512, под круг) + banner.png (960x540)
./gradlew genDiscordEmoji    # build/discord/emoji/*.png — 15 штук, 128px, прозрачные, акцент #7CABFF
```

**Иконка** — не та же, что `docs/icon.png`. Discord режет иконку **в круг**, поэтому знак нарисован
крупнее (66% плитки против 58%): знак, рассчитанный на квадрат, в круге выглядит мелким, а в боковой
панели он рендерится в 48px.

**Баннер** требует буст-уровня 1 (два буста). Не покупай их сейчас: на сервере никого, украшения
некому увидеть. Купи в неделю анонса — тогда фон приглашения будет первым, что увидит человек,
пришедший с Modrinth.

**Эмодзи** отрисованы той же SDF-математикой, что и шейдер в игре, — значок в чате это буквально тот
же значок, что в меню мода. Свой иконочный набор есть у единиц; мемные эмодзи есть у всех.

---

## 2. Категории и каналы

Деление настоящее: **где говорит студия** и **где говорят люди**.

```
CLUB                        читают. Пишешь только ты (и GitHub).
   📌│rules
   📢│announcements
   🔄│changelog

COMMUNITY                   говорят. Пишут все.
   💬│general
   🛠│support
   🖼│showcase
```

Черта `│` (U+2502) не декоративная: она выстраивает имена в колонку, потому что значки разной ширины.
Без неё левый край имён плывёт.

Имена каналов копируй **точно** (значок, `│`, имя, без пробелов вокруг черты):

```
📌│rules
📢│announcements
🔄│changelog
💬│general
🛠│support
🖼│showcase
```

**Чего не делаем:** голосового канала (вечное «0 участников» — самый громкий сигнал мёртвого сервера),
категории под будущие продукты, каналов `off-topic` / `memes` (придут, когда будет кому).

---

## 3. Права

| Канал | `@everyone` |
|---|---|
| `rules`, `announcements`, `changelog` | **Отправлять сообщения — ВЫКЛ.** Читать — вкл. |
| `general`, `support`, `showcase` | всё как обычно |

Правый клик по каналу → Настройки → Права доступа → роль `@everyone` → «Отправлять сообщения» в ✗.

**Роли — две. Пока именно две:**

| Роль | Цвет | Кому |
|---|---|---|
| Club Dev | `#7CABFF` | тебе |
| `@everyone` | — | всем остальным |

**`Contributor` (цвет `#8B97A8`) — НЕ создавать сейчас.** Она для тех, чей pull request влили на
GitHub, а таких людей ноль. Роль, которую не носит никто, — это та же пустая полка, от которой мы
отказались в каналах, только в списке участников. Заведёшь её в тот день, когда вольёшь первый чужой
PR: это минута работы, и в этот момент она будет означать что-то настоящее.

Никаких «VIP», «Легенда», «Актив» — это язык игровых серверов, а не студии.

---

## 4. Настройки сервера

- **Включить Community** (Настройки сервера → Enable Community). Даёт бесплатно:
  - **Rules Screening** — человек обязан принять правила до того, как сможет писать. Само по себе
    читается как «сюда не заходят с ноги».
  - `announcements` можно сделать **Announcement Channel** — другие серверы смогут на него подписаться.
- **Verification level: Medium** — подтверждённая почта + 5 минут на сервере. Отсекает рейд-ботов,
  не мешая людям.
- **Описание сервера** (оно видно в окне приглашения):

  > Club — a first-person utility client for Minecraft (Fabric 1.21.1). Open source, MIT. Not a cheat client.

---

## 5. Вебхук GitHub → `#changelog`

Ноль обслуживания, а эффект сильный: человек заходит и видит, что проект **живёт**.

1. Discord: правый клик по `🔄│changelog` → Настройки → **Интеграции** → **Вебхуки** → Создать →
   **Копировать URL**.
2. GitHub: `github.com/ClubClient/clubclient` → Settings → Webhooks → **Add webhook**.
   - Payload URL: вставленный URL **+ `/github`** в конце.
   - Content type: `application/json`
   - **Let me select individual events** → отметить только **Releases** (и, если хочешь, **Pushes**).
3. Save.

> `/github` в конце обязателен. Без него Discord не поймёт формат и вебхук будет молча падать.

---

## 6. Тексты

Посты — на двух языках, **в одном сообщении**: сначала английский, потом русский, разделены линией.
Английский первым, потому что трафик пойдёт с Modrinth и GitHub.

### 6.1. Закреп в `📌│rules`

```
**Club** — a first-person utility client for Minecraft 1.21.1 (Fabric).
Zoom, fullbright, freelook, item scrolling, a movable HUD. Open source, MIT.

📥  **Download** — https://modrinth.com/mod/clubclient
💻  **Source** — https://github.com/ClubClient/clubclient
🐛  **Bugs** — https://github.com/ClubClient/clubclient/issues

**Rules**

**1. Club is not a cheat client.**
No killaura, no ESP, no reach, no autoclicker, no X-ray. This is not a temporary stance and it is not
up for discussion — requests for these are closed without a reply. Everything Club does is about your
own view of the game and your own convenience at the keyboard.

**2. A bug goes to GitHub, not here.**
`#support` is for getting set up. A bug reported in chat is a bug that gets lost — the tracker has a
form, a history, and a search. https://github.com/ClubClient/clubclient/issues

**3. In `#support`, bring the evidence.**
Your full mod list, and `latest.log` as a **file** (it lives in `.minecraft/logs/`). Not a screenshot
of it — we cannot search a picture, and the line that matters is almost never the one on screen.

**4. No advertising, no drama, no hostility.**
That is the whole of it.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**Club** — клиентский мод для Minecraft 1.21.1 (Fabric).
Зум, фулбрайт, фрилук, перенос предметов колесом, перетаскиваемый HUD. Открытые исходники, MIT.

📥  **Скачать** — https://modrinth.com/mod/clubclient
💻  **Исходники** — https://github.com/ClubClient/clubclient
🐛  **Баги** — https://github.com/ClubClient/clubclient/issues

**Правила**

**1. Club — не чит-клиент.**
Killaura, ESP, reach, автокликер, X-ray — этого не будет. Это не временная позиция и не предмет для
обсуждения: такие просьбы закрываются без ответа. Всё, что делает Club, — про твой собственный взгляд
на игру и твоё удобство за клавиатурой.

**2. Баг — на GitHub, а не сюда.**
`#support` — это «помогите настроить». Баг, написанный в чат, — это потерянный баг: в трекере есть
форма, история и поиск.

**3. В `#support` приходи с доказательствами.**
Полный список модов и `latest.log` **файлом** (лежит в `.minecraft/logs/`). Не скриншотом — по
картинке нельзя искать, а нужная строка почти никогда не та, что видна на экране.

**4. Без рекламы, драмы и хамства.**
Это всё.
```

### 6.2. Закреп в `🛠│support`

```
**Before you post, bring these four things.** Without them the answer is always the same question, and
we both lose a day.

**1.** Your **Club version** (the mod list in-game, or the jar's filename).
**2.** Your **full mod list** — every mod, not just the ones you suspect. A conflict is usually with
the mod you would never have mentioned.
**3.** `latest.log`, as a **file** — drag it into the chat. It is in `.minecraft/logs/`. If the game
crashed, the crash report from `.minecraft/crash-reports/` instead.
**4.** Does it still happen with **only Club and Fabric API** installed? This one question separates
our bug from a conflict, and it saves days. "I haven't tested that" is a fine answer — just say it.

And tell us if you run **Sodium**, **Iris**, a **shaderpack**, or **Freecam**. They rewrite the same
rendering Club hooks into, so they matter more than the rest of your list.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**Прежде чем писать, собери четыре вещи.** Без них ответ всегда один и тот же вопрос, и мы оба теряем
день.

**1.** **Версия Club** (список модов в игре или имя джарника).
**2.** **Полный список модов** — все, а не только подозрительные. Конфликт обычно с тем модом, который
ты бы и не подумал назвать.
**3.** `latest.log` **файлом** — перетащи его в чат. Лежит в `.minecraft/logs/`. Если игра упала —
краш-репорт из `.minecraft/crash-reports/`.
**4.** Воспроизводится ли **только с Club и Fabric API**? Этот один вопрос отделяет наш баг от
конфликта и экономит дни. «Не проверял» — нормальный ответ, просто скажи это.

И скажи, стоят ли у тебя **Sodium**, **Iris**, **шейдерпак** или **Freecam**. Они переписывают тот же
рендер, в который встраивается Club, — они важнее всего остального списка.
```

### 6.3. Шаблон поста в `📢│announcements`

Заполняется на релизе. Тон — как у `CHANGELOG.md`: говорим, что изменилось и почему, не рекламируем.

```
## Club vX.Y.Z

<одно предложение: что это за релиз>

<2–4 пункта: что изменилось. Числа — только те, что воспроизводит бенч.>

📥 https://modrinth.com/mod/clubclient
📄 Full changelog: https://github.com/ClubClient/clubclient/releases

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## Club vX.Y.Z

<то же по-русски>
```

---

## 7. Порядок действий

1. Настройки сервера → **иконка** (`build/discord/icon.png`), **имя** `Club`, **описание** (§4).
2. Создать две категории: `Club`, `Community`.
3. Создать шесть каналов (имена — §2), разложить по категориям.
4. Права `@everyone` на трёх верхних каналах (§3).
5. Роль `Club Dev` (§3). `Contributor` — не сейчас.
6. Включить **Community**, **Rules Screening**, **Verification: Medium** (§4).
7. Настройки сервера → **Эмодзи** → загрузить 15 PNG из `build/discord/emoji/`.
8. Вебхук GitHub → `#changelog` (§5).
9. Запостить и **закрепить** тексты в `rules` и `support` (§6).
10. Баннер — **потом**, когда будут бусты и трафик.
