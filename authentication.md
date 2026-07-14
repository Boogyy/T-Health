# Аутентификация и пользователи

## 1. Общая схема

В проекте используется Keycloak. Backend:

- не регистрирует пользователей самостоятельно;
- не хранит пароли;
- не выпускает JWT;
- работает как OAuth2 Resource Server;
- проверяет access token, выданный realm `t-health`.

Основной пользовательский flow:

```text
Frontend
→ Keycloak Authorization Code Flow + PKCE
→ access token
→ запрос к Spring Boot с Bearer token
→ проверка JWT
→ вызов защищённого API
```

## 2. Компоненты

| Компонент | Адрес / значение |
|---|---|
| Keycloak | `http://localhost:8180` |
| Realm | `t-health` |
| Frontend client | `t-health-frontend` |
| Backend test client | `t-health-backend` |
| Admin service client | `t-health-admin` |
| Frontend | `http://localhost:5173` |
| Backend | `http://localhost:8080` |

### `t-health-frontend`

Публичный OIDC-клиент для браузера:

- Standard Flow включён;
- PKCE: `S256`;
- client secret не используется;
- redirect URI для локального frontend: `http://localhost:5173/*`.

### `t-health-backend`

Публичный клиент с Direct Access Grants. Используется только для ручного локального получения токена через `grant_type=password`. Это не основной flow приложения.

### `t-health-admin`

Confidential service-account client. Backend использует его для вызовов Keycloak Admin API, в частности при удалении пользователя.

Service account должен иметь роли `realm-management`:

```text
view-users
query-users
manage-users
```

Client secret хранится только локально в `.env`.

## 3. Вход и регистрация во frontend

Frontend формирует PKCE:

1. создаёт `code_verifier`;
2. вычисляет `code_challenge` методом `S256`;
3. сохраняет `state` и verifier;
4. перенаправляет браузер в Keycloak.

Вход:

```text
GET /realms/t-health/protocol/openid-connect/auth
```

Регистрация:

```text
GET /realms/t-health/protocol/openid-connect/registrations
```

Основные параметры:

```text
client_id=t-health-frontend
response_type=code
scope=openid profile email
redirect_uri=http://localhost:5173/
code_challenge_method=S256
```

После возврата `code` frontend отправляет запрос:

```http
POST http://localhost:8180/realms/t-health/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded
```

```text
grant_type=authorization_code
client_id=t-health-frontend
code=<authorization_code>
redirect_uri=http://localhost:5173/
code_verifier=<pkce_verifier>
```

Frontend сохраняет access, refresh и ID token в `localStorage`. Перед API-запросом access token добавляется в заголовок:

```http
Authorization: Bearer <access_token>
```

Если access token скоро истекает, frontend обновляет его через refresh token.

## 4. Проверка JWT в backend

В `application.properties`:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/t-health
```

Spring Security:

- проверяет подпись JWT;
- проверяет issuer;
- проверяет срок действия;
- извлекает realm roles из `realm_access.roles`;
- преобразует их в `ROLE_<role>`.

Например:

```text
USER  → ROLE_USER
ADMIN → ROLE_ADMIN
```

Маршруты:

```text
/health, Swagger, OpenAPI → без токена
/api/**                   → требуется JWT
/api/admin/**             → требуется ROLE_ADMIN
```

Отдельных административных endpoint'ов в текущем MVP нет.

## 5. Локальный профиль пользователя

Keycloak хранит:

- учётную запись;
- пароль;
- email;
- имя и фамилию;
- роли;
- пользовательскую сессию.

PostgreSQL хранит локальный профиль и бизнес-данные приложения. Пароль и роль в таблице `users` не хранятся.

При первом запросе:

```http
GET /api/users/me
Authorization: Bearer <access_token>
```

backend ищет пользователя по claim `sub`. Если записи нет, профиль создаётся автоматически.

Соответствие claims:

| JWT claim | Поле профиля |
|---|---|
| `sub` | `keycloak_id` / `id` |
| `preferred_username` | `username` |
| `email` | `email` |
| `given_name` | `first_name` |
| `family_name` | `last_name` |

`email` обязателен. Если `preferred_username` отсутствует, создаётся значение:

```text
user_<sub>
```

После создания профиля пользователю выдаётся достижение `WELCOME_TO_T_HEALTH`.

## 6. Выход

Frontend перенаправляет браузер на:

```text
http://localhost:8180/realms/t-health/protocol/openid-connect/logout
```

Передаются:

```text
client_id=t-health-frontend
post_logout_redirect_uri=http://localhost:5173/
id_token_hint=<id_token>
```

Локальные token-данные очищаются.

## 7. Настройка Keycloak Admin Client

Backend требует переменные:

```env
KEYCLOAK_BASE_URL=http://localhost:8180
KEYCLOAK_REALM=t-health
KEYCLOAK_ADMIN_CLIENT_ID=t-health-admin
KEYCLOAK_ADMIN_CLIENT_SECRET=<secret>
```

Чтобы получить secret:

1. запусти Keycloak;
2. открой `http://localhost:8180/admin`;
3. войди как `admin / admin`;
4. выбери realm `t-health`;
5. открой `Clients → t-health-admin → Credentials`;
6. скопируй или перегенерируй Client secret;
7. добавь его в `.env`;
8. перезапусти backend.

Не коммить secret в Git.

## 8. Ручное получение токена

Для проверки API без frontend можно использовать Direct Access Grant клиента `t-health-backend`.

Сначала создай пользователя в Keycloak и установи ему постоянный пароль. Затем:

```bash
curl -s -X POST \
  "http://localhost:8180/realms/t-health/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=t-health-backend" \
  -d "username=testuser" \
  -d "password=testpassword"
```

Возьми `access_token` и вызови backend:

```bash
curl -i "http://localhost:8080/api/users/me" \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

Без токена:

```bash
curl -i "http://localhost:8080/api/users/me"
```

ожидается:

```text
401 Unauthorized
```

Direct Access Grant применяется только для локального тестирования. Пользовательский интерфейс использует Authorization Code + PKCE.

## 9. Swagger

Открой:

```text
http://localhost:8080/swagger-ui.html
```

Нажми **Authorize** и введи access token. В зависимости от интерфейса Swagger можно указать либо сам токен, либо:

```text
Bearer <access_token>
```

Для схемы `bearerAuth` обычно достаточно вставить только значение access token.

## 10. Импорт realm

Конфигурация хранится в:

```text
keycloak/t-health-realm.json
```

Docker Compose запускает Keycloak так:

```yaml
command: start-dev --import-realm
```

Импорт выполняется при создании realm. Если realm `t-health` уже существует, изменения JSON могут не примениться автоматически.

Пересоздать только контейнер Keycloak:

```bash
docker compose stop keycloak
docker compose rm -f keycloak
docker compose up -d keycloak
```

Полная очистка окружения:

```bash
docker compose down -v
docker compose up -d
```

`docker compose down -v` удаляет также volume PostgreSQL и все локальные данные.

## 11. Типичные ошибки

### Login не открывает Keycloak

Проверь:

```bash
docker compose ps
curl -i http://localhost:8180/realms/t-health/.well-known/openid-configuration
```

### `Realm does not exist`

Realm не импортирован. Пересоздай контейнер Keycloak или импортируй `keycloak/t-health-realm.json` вручную.

### `Client not found`

Проверь наличие клиента `t-health-frontend` в realm `t-health`.

### `Invalid parameter: redirect_uri`

В клиенте `t-health-frontend` должен быть разрешён:

```text
http://localhost:5173/*
```

### Backend возвращает `401`

Проверь:

- токен не истёк;
- token получен из realm `t-health`;
- issuer равен `http://localhost:8180/realms/t-health`;
- запрос содержит `Authorization: Bearer ...`.

### Backend не запускается из-за `KEYCLOAK_ADMIN_CLIENT_SECRET`

Добавь настоящий secret клиента `t-health-admin` в `.env`.

### Удаление профиля возвращает ошибку Keycloak

Проверь service account и роли клиента `t-health-admin`:

```text
view-users
query-users
manage-users
```
