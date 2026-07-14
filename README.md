# T-Health

T-Health — учебное веб-приложение для ведения тренировок, питания и рецептов, публикации активности, достижений, сообществ, комментариев и личных чатов.

Backend: Java 17, Spring Boot, Spring Security, Keycloak, PostgreSQL, Flyway.  
Frontend: Vite и JavaScript.

## Требования

- JDK 17+
- Docker Desktop / Docker Engine
- Node.js и npm
- Git

## Быстрый запуск

### 1. Подготовить переменные окружения

Из корня проекта:

```bash
cp .env.example .env
```

Проверь `.env`:

```env
POSTGRES_DB=t_health_db
POSTGRES_USER=t_health_user
POSTGRES_PASSWORD=change_me
POSTGRES_PORT=5432
SERVER_PORT=8080

KEYCLOAK_BASE_URL=http://localhost:8180
KEYCLOAK_REALM=t-health
KEYCLOAK_ADMIN_CLIENT_ID=t-health-admin
KEYCLOAK_ADMIN_CLIENT_SECRET=replace_with_real_secret
```

### 2. Запустить PostgreSQL и Keycloak

```bash
docker compose up -d
docker compose ps
```

Keycloak:

```text
http://localhost:8180
```

Административная консоль:

```text
http://localhost:8180/admin
login: admin
password: admin
```

После первого запуска открой:

```text
Realm t-health
→ Clients
→ t-health-admin
→ Credentials
```

Скопируй `Client secret` в `KEYCLOAK_ADMIN_CLIENT_SECRET` файла `.env`.

### 3. Запустить backend

```bash
./mvnw spring-boot:run
```

Проверка:

```bash
curl http://localhost:8080/health
```

Должен вернуться текст:

```text
T-Health is running
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

### 4. Запустить frontend

```bash
cd frontend
npm install
```

Создай `frontend/.env`:

```env
VITE_BACKEND_URL=http://localhost:8080
VITE_KEYCLOAK_BASE_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=t-health
VITE_KEYCLOAK_CLIENT_ID=t-health-frontend
```

Запусти:

```bash
npm run dev
```

Открой:

```text
http://localhost:5173
```

## Тесты

Только unit-тесты:

```bash
./mvnw clean test
```

Полная проверка unit- и integration-тестов:

```bash
docker info
./mvnw clean verify
```

Подробности: [docs/testing.md](docs/testing.md).

## Остановка

Остановить инфраструктуру без удаления данных:

```bash
docker compose down
```

Удалить контейнеры и локальный volume PostgreSQL:

```bash
docker compose down -v
```

Команда с `-v` безвозвратно удаляет локальные данные.

## Документация

- [API-контракт](docs/api-contract.md)
- [Аутентификация](authentication.md)
- [Техническое задание](docs/technical-specification.md)
- [ER-диаграмма](docs/er-diagram.md)
- [Тестирование](docs/testing.md)
