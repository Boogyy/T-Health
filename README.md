# Т-Здоровье

Backend-приложение в рамках проектного практикума

## Стек

- Java 17
- Spring Boot 3.5.14
- Maven
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Validation
- Swagger/OpenAPI
- JUnit 5
- Testcontainers

## Локальный запуск

1. Создать `.env` на основе `.env.example`:

```bash
  cp .env.example .env
````

2. Запустить PostgreSQL:

```bash
  docker compose up -d
```

3. Запустить приложение:

```bash
  ./mvnw spring-boot:run
```

Приложение будет доступно по адресу:

```text
http://localhost:8080
```

Если порт занят, укажите другой `SERVER_PORT` в `.env`.

## Swagger

```text
http://localhost:8080/swagger-ui.html
```

## Проверка health endpoint

```text
GET /health
```

## Frontend

В проект добавлен статический frontend, который собирается вместе со Spring Boot и доступен на корневом пути backend-приложения:

```text
http://localhost:8089/
```

Frontend реализует текущий пользовательский сценарий:

- страницы Register/Login через Keycloak;
- после callback получает OAuth2 code, обменивает его на token по Authorization Code + PKCE;
- сразу вызывает `GET /api/users/me`, чтобы backend создал локальный профиль пользователя в PostgreSQL;
- показывает профиль с фото-заглушкой, username, email и карточками «Все тренировки» / «Все приемы пищи»;
- показывает списки, детали, создание, редактирование и удаление тренировок и приемов пищи;
- содержит заглушку навигации для будущей ленты постов.

По умолчанию используются настройки:

```text
KEYCLOAK: http://localhost:8180/realms/t-health
CLIENT:   t-health-frontend
API:      текущий origin backend, например http://localhost:8089
```

Если frontend запускается отдельно на `localhost:5173`, API автоматически указывает на `http://localhost:8089`. Для другого адреса можно задать настройки через `localStorage` в браузере:

```js
localStorage.setItem('tHealthApiBase', 'http://localhost:8089');
localStorage.setItem('tHealthKeycloakUrl', 'http://localhost:8180');
localStorage.setItem('tHealthRealm', 't-health');
localStorage.setItem('tHealthClientId', 't-health-frontend');
```
