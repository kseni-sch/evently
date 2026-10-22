# Evently

Evently — приложение для поиска и персонального подбора локальных городских событий: акций брендов и заведений, открытых тренировок, мастер-классов, маркетов, небольших фестивалей и временных pop-up мероприятий.

## Идея

Информация об интересных мероприятиях часто разбросана по разным источникам: сайтам, афишам, Telegram-каналам и страницам городских пространств.

Из-за этого легко пропустить небольшие локальные события, временные акции, маркеты, бесплатные активности, мастер-классы и другие мероприятия.

Evently будет собирать события из разных источников в одном месте и формировать персональную ленту для пользователя.

## Основные возможности

Пользователь сможет:
- просматривать актуальные события;
- использовать фильтры по дате, стоимости и другим параметрам;
- сохранять понравившиеся события;
- выбирать интересующие категории;
- описывать свои интересы в свободной форме;
- получать персональные рекомендации.

## Первая лабораторная

Сервер работает как модульный монолит: одно приложение с модулями `app`, `collector`, `events`, `users`, `favorites` и `recommendations`.

Реализованы:

- сбор последних 100 постов из [Афиши Петербурга](https://t.me/afishapeterburga) и извлечение мероприятий через Groq;
- каталог с фильтрами по датам, цене и категории;
- объединение явных дубликатов с сохранением ссылок на публикации;
- регистрация и вход через Keycloak, профиль и избранное;
- рекомендации по интересам пользователя в свободной форме.

Данные хранятся в PostgreSQL 18, миграции выполняются через Liquibase.
При неоднозначном совпадении мероприятия не объединяются.

## Запуск

Нужен Docker Compose. Порты `5432`, `8080` и `8081` должны быть свободны.

```bash
cp .env.example .env
```

В `.env` заполнить `GROQ_API_KEY` и задать `POSTGRES_PASSWORD` и `KEYCLOAK_ADMIN_PASSWORD`.
Ключ Groq создаётся в [личном кабинете](https://console.groq.com/keys).

```bash
docker compose up -d --build
```

Сервер доступен на `http://localhost:8080`, Keycloak — на `http://localhost:8081`.
Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html). Для защищённых запросов нажать **Authorize** и вставить access token без префикса `Bearer`.
Сбор начинается автоматически и повторяется каждые 15 минут. Первое заполнение каталога занимает время и зависит от лимитов Groq.

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/events
curl http://localhost:8080/api/categories
curl 'http://localhost:8080/api/events?category=EXHIBITION&maxPrice=500'
```

По умолчанию показываются текущие и будущие события. Для периода используются параметры `dateFrom` и `dateTo`, для страниц — `page` и `size`.
Логи: `docker compose logs -f app`. Остановка: `docker compose down`. Данные при обычной остановке сохраняются.

## Личные данные и рекомендации

Для входа используется OAuth 2.0 Authorization Code с PKCE S256. В OAuth-клиенте указать:

- Client ID: `evently`, без client secret;
- Authorization URL: `http://localhost:8081/realms/evently/protocol/openid-connect/auth`;
- Token URL: `http://localhost:8081/realms/evently/protocol/openid-connect/token`;
- Redirect URI: `http://localhost:8080/callback`;
- Scope: `openid profile email`.

На странице входа можно зарегистрироваться. Access token передаётся в заголовке `Authorization: Bearer <access_token>`.

- `GET /api/users/me` — профиль, `PUT /api/users/me` — обновление полей `name` и `interests`.
- `GET /api/favorites` — избранное, `PUT` и `DELETE /api/favorites/{eventId}` — добавить и удалить событие.
- `GET /api/recommendations?limit=10` — рекомендации. Перед запросом нужно заполнить интересы в профиле.

Ответ LLM может занять минуту и дольше. Если Groq недоступен, рекомендации возвращают HTTP 503.

## Тесты

Нужны JDK 21 и работающий Docker. Тесты поднимают отдельную базу PostgreSQL 18.

```bash
cd server
./gradlew test
```
