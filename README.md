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