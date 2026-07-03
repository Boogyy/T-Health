## Auth и пользователи

В проекте используется Keycloak. Backend больше не регистрирует пользователей, не хранит пароли и не выпускает JWT.

### Как работает flow

```text
Пользователь -> Keycloak login/register -> access_token -> Backend
```

1. Пользователь регистрируется или логинится через Keycloak.
2. Keycloak выдает `access_token`.
3. Клиент отправляет запросы в backend:

```http
Authorization: Bearer <access_token>
```

4. Backend проверяет токен через Keycloak.
5. Backend берет id пользователя из JWT claim `sub`.
6. При первом запросе backend создает локальный профиль пользователя в PostgreSQL.

### Что хранится в Keycloak

Keycloak отвечает за:

* регистрацию;
* логин;
* пароли;
* JWT;
* роли `USER` / `ADMIN`;
* страницу входа и регистрации.

Realm:

```text
t-health
```

Client для локального тестирования:

```text
t-health-backend
```

### Что хранится в PostgreSQL

В PostgreSQL хранится только профиль пользователя и бизнес-данные приложения.

```sql
users (
  keycloak_id UUID PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  email VARCHAR(64) NOT NULL,
  first_name VARCHAR(64),
  last_name VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP
)
```

В таблице `users` больше нет `password_hash` и `role`.

`keycloak_id` = `sub` из JWT:

```java
UUID userId = UUID.fromString(jwt.getSubject());
```

Все сущности, связанные с пользователем, должны ссылаться на `users.keycloak_id`.

### Настройка Spring

В `application.properties`:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/t-health
```

Backend работает как OAuth2 Resource Server: он только проверяет токен, но не создает его.

### Регистрация пользователя

Регистрация идет через Keycloak:

```text
http://localhost:8180/realms/t-health/protocol/openid-connect/registrations?client_id=t-health-backend&response_type=code&scope=openid%20profile%20email&redirect_uri=http://localhost:8089
```
Если порт отличается, надо использовать соответствующий redirect_uri.

После регистрации пользователь появляется в Keycloak. В локальной БД он появится после первого запроса:

```http
GET /api/users/me
```

### Получение токена для тестов

```bash
curl -X POST "http://localhost:8180/realms/t-health/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=t-health-backend" \
  -d "username=testuser" \
  -d "password=testpassword"
```

Из ответа нужно взять `access_token`.

### Проверка backend

```bash
curl -i http://localhost:8089/api/users/me \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

Без токена backend должен вернуть `401`.

### Keycloak config

Настройки realm хранятся в проекте:

```text
keycloak/t-health-realm.json
```

В `docker-compose.yml`:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26.3.0
  command: start-dev --import-realm
  environment:
    KC_BOOTSTRAP_ADMIN_USERNAME: admin
    KC_BOOTSTRAP_ADMIN_PASSWORD: admin
  ports:
    - "8180:8080"
  volumes:
    - ./keycloak:/opt/keycloak/data/import
```

Если realm уже существует, Keycloak не перезапишет его при старте. Для полной пересборки локального окружения:

```bash
docker compose down -v
docker compose up -d
```