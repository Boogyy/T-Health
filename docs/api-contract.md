# API-контракт T-Health

Документ описывает фактический REST API текущей версии backend.

## 1. Общие правила

### Адреса

```text
Backend:    http://localhost:8080
API prefix: http://localhost:8080/api
Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI:    http://localhost:8080/v3/api-docs
Health:     http://localhost:8080/health
```

Порт backend задаётся переменной `SERVER_PORT`. В примерах используется значение `8080`.

### Авторизация

Все маршруты `/api/**` требуют access token Keycloak:

```http
Authorization: Bearer <access_token>
```

Без токена backend возвращает `401 Unauthorized`.

Открыты без авторизации:

```text
GET /health
/swagger-ui/**
/swagger-ui.html
/v3/api-docs/**
```

Регистрация, вход, обновление и завершение сессии выполняются через Keycloak. В backend нет собственных endpoint'ов `/auth/login` или `/auth/register`.

### Форматы

- идентификаторы: UUID;
- дата: `YYYY-MM-DD`;
- дата и время: ISO 8601, например `2026-07-14T18:30:00`;
- тело запроса: `application/json`;
- списковые endpoint'ы возвращают JSON-массивы;
- пагинация в текущей версии не реализована;
- неизвестный UUID, enum или дата возвращают `400`, а не `500`.

### Enum

```text
WorkoutType:     CARDIO, STRENGTH, STRETCHING
PostType:        TEXT, WORKOUT, RECIPE, ACHIEVEMENT
PostVisibility:  PUBLIC, COMMUNITY
CommunityRole:   OWNER, MODERATOR, MEMBER
```

`PUBLIC` означает видимость во всей авторизованной ленте приложения. Публичные посты не доступны анонимно, поскольку `/api/**` защищён JWT.

### Формат ошибки

```json
{
  "status": 400,
  "message": "Validation failed",
  "path": "/api/recipes",
  "errorTime": "2026-07-14T18:30:00",
  "validationErrors": {
    "title": "must not be null"
  }
}
```

`validationErrors` присутствует для ошибок Bean Validation и может быть `null` для остальных ошибок.

Основные статусы:

| Статус | Значение |
|---:|---|
| `200` | запрос выполнен |
| `201` | ресурс создан |
| `204` | ресурс удалён, тело отсутствует |
| `400` | неверное тело, параметр, UUID, дата или enum |
| `401` | отсутствует или недействителен JWT |
| `403` | пользователь аутентифицирован, но не имеет прав |
| `404` | ресурс или принадлежащая пользователю сущность не найдены |
| `409` | конфликт уникальности или связанных данных |

---

## 2. Health API

| Метод | Endpoint | Авторизация | Ответ |
|---|---|---|---|
| GET | `/health` | нет | `200`, строка `T-Health is running` |

---

## 3. Users API

При первом `GET /api/users/me` backend создаёт локальный профиль из JWT, если его ещё нет. Используются claims:

```text
sub                → id
preferred_username → username
email              → email
given_name         → firstName
family_name        → lastName
```

`email` обязателен. При отсутствии `preferred_username` создаётся username вида `user_<sub>`.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/users/me` | получить или создать локальный профиль | `200 UserResponse` |
| PATCH | `/api/users/me` | частично обновить профиль | `200 UserResponse` |
| DELETE | `/api/users/me` | удалить локальный профиль и пользователя Keycloak | `204` |

### PATCH `/api/users/me`

Все поля необязательны.

```json
{
  "username": "george",
  "firstName": "George",
  "lastName": "Ivanov"
}
```

Ограничения:

| Поле | Ограничение |
|---|---|
| `username` | до 32 символов, должен быть уникальным |
| `firstName` | до 64 символов |
| `lastName` | до 64 символов |

### UserResponse

```json
{
  "id": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "username": "george",
  "email": "george@example.com",
  "firstName": "George",
  "lastName": "Ivanov"
}
```

---

## 4. Achievements API

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/achievements` | список всех доступных достижений | `200 AchievementResponse[]` |
| GET | `/api/users/me/achievements` | достижения текущего пользователя | `200 UserAchievementResponse[]` |

### AchievementResponse

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "code": "FIRST_WORKOUT",
  "title": "Первая тренировка",
  "description": "Добавьте первую тренировку",
  "createdAt": "2026-07-14T18:30:00"
}
```

### UserAchievementResponse

```json
{
  "id": "8a4a34d4-7a56-4ac3-a3a3-f0d7b6a99991",
  "userId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "achievement": {
    "id": "11111111-1111-1111-1111-111111111111",
    "code": "FIRST_WORKOUT",
    "title": "Первая тренировка",
    "description": "Добавьте первую тренировку",
    "createdAt": "2026-07-14T18:30:00"
  },
  "receivedAt": "2026-07-14T18:31:00"
}
```

---

## 5. Workouts API

Тренировки являются личными ресурсами. Получение, изменение и удаление выполняются только для тренировки текущего пользователя. Для чужого UUID возвращается `404`.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/workouts` | все тренировки текущего пользователя | `200 WorkoutResponse[]` |
| GET | `/api/workouts/{id}` | одна собственная тренировка | `200 WorkoutResponse` |
| POST | `/api/workouts` | создать тренировку без публикации | `201 WorkoutResponse` |
| PATCH | `/api/workouts/{id}` | частично обновить тренировку | `200 WorkoutResponse` |
| DELETE | `/api/workouts/{id}?deleteRelatedPost=false` | удалить тренировку | `204` |

Если тренировка опубликована, удаление без `deleteRelatedPost=true` возвращает `409`. При подтверждении удаляются связанный пост и тренировка.

### POST `/api/workouts`

```json
{
  "title": "Силовая тренировка",
  "type": "STRENGTH",
  "description": "Жим, приседания, тяга",
  "durationMinutes": 45,
  "caloriesBurned": 320
}
```

Ограничения:

- `title`: обязательная непустая строка, до 128 символов;
- `type`: обязательный `CARDIO`, `STRENGTH` или `STRETCHING`;
- `description`: до 2000 символов;
- `durationMinutes`: обязательное число больше нуля;
- `caloriesBurned`: число не меньше нуля;
- дата тренировки в текущем запросе не передаётся и устанавливается сервером.

### PATCH `/api/workouts/{id}`

Все поля необязательны:

```json
{
  "title": "Обновлённая тренировка",
  "durationMinutes": 60,
  "caloriesBurned": 450
}
```

### WorkoutResponse

```json
{
  "id": "6b6c2f64-3a9b-4c37-b6d1-4e6b5e1a1111",
  "userId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "title": "Силовая тренировка",
  "type": "STRENGTH",
  "description": "Жим, приседания, тяга",
  "durationMinutes": 45,
  "caloriesBurned": 320,
  "workoutDate": "2026-07-14T18:30:00"
}
```

---

## 6. Food Entries API

Записи питания являются личными ресурсами. Для чужой записи возвращается `404`.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/food-entries` | все записи текущего пользователя | `200 FoodEntryResponse[]` |
| GET | `/api/food-entries/{id}` | одна собственная запись | `200 FoodEntryResponse` |
| GET | `/api/food-entries/daily?date=YYYY-MM-DD` | записи и сумма КБЖУ за день | `200 DailyFoodEntriesResponse` |
| POST | `/api/food-entries` | создать запись | `201 FoodEntryResponse` |
| PATCH | `/api/food-entries/{id}` | частично обновить запись | `200 FoodEntryResponse` |
| DELETE | `/api/food-entries/{id}` | удалить запись | `204` |

Если `date` в `/daily` не передана, используется текущая дата сервера.

### POST `/api/food-entries`

```json
{
  "mealName": "Овсянка с ягодами",
  "calories": 350,
  "proteins": 12.5,
  "fats": 8.0,
  "carbohydrates": 55.0,
  "mealDate": "2026-07-14T09:00:00"
}
```

`mealDate` необязателен; при отсутствии сервер устанавливает текущее время.

Обязательные значения КБЖУ не могут быть отрицательными. `mealName` — до 128 символов.

### PATCH `/api/food-entries/{id}`

Все поля необязательны:

```json
{
  "mealName": "Овсянка с бананом",
  "calories": 410
}
```

### DailyFoodEntriesResponse

```json
{
  "date": "2026-07-14",
  "totalCalories": 1850,
  "totalProteins": 105.5,
  "totalFats": 62.3,
  "totalCarbohydrates": 210.0,
  "entries": []
}
```

---

## 7. Recipes API

Рецепты являются личными ресурсами. Создание рецепта через этот API не публикует его в ленте.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/recipes` | все рецепты текущего пользователя | `200 RecipeResponse[]` |
| GET | `/api/recipes/{id}` | один собственный рецепт | `200 RecipeResponse` |
| POST | `/api/recipes` | создать рецепт без публикации | `201 RecipeResponse` |
| PATCH | `/api/recipes/{id}` | частично обновить рецепт | `200 RecipeResponse` |
| DELETE | `/api/recipes/{id}?deleteRelatedPost=false` | удалить рецепт | `204` |

Если рецепт опубликован, удаление без `deleteRelatedPost=true` возвращает `409`. При подтверждении удаляются связанный пост и рецепт.

### POST `/api/recipes`

```json
{
  "title": "Овсянка с ягодами",
  "description": "Полезный завтрак",
  "ingredients": "60 г овсяных хлопьев; 200 мл молока; 100 г ягод",
  "cookingSteps": "1. Смешать хлопья и молоко. 2. Варить 5–7 минут. 3. Добавить ягоды.",
  "calories": 350,
  "proteins": 12.5,
  "fats": 8.0,
  "carbohydrates": 55.0,
  "imageUrl": "https://example.org/images/oatmeal.jpg"
}
```

Ограничения:

| Поле | Ограничение |
|---|---|
| `title` | обязательно, до 128 символов |
| `description` | обязательно, до 512 символов |
| `ingredients` | обязательно, до 2000 символов |
| `cookingSteps` | обязательно, до 4000 символов |
| КБЖУ | необязательно, не меньше нуля |
| `imageUrl` | необязательно, валидный URL, до 512 символов |

### PATCH `/api/recipes/{id}`

Все поля необязательны:

```json
{
  "description": "Обновлённое описание",
  "cookingSteps": "1. Подготовить ингредиенты. 2. Приготовить блюдо."
}
```

### RecipeResponse

```json
{
  "id": "6b6c2f64-3a9b-4c37-b6d1-4e6b5e1a1111",
  "authorId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "title": "Овсянка с ягодами",
  "description": "Полезный завтрак",
  "ingredients": "Овсяные хлопья, молоко, ягоды",
  "cookingSteps": "Смешать, сварить, добавить ягоды",
  "calories": 350,
  "proteins": 12.5,
  "fats": 8.0,
  "carbohydrates": 55.0,
  "imageUrl": "https://example.org/images/oatmeal.jpg",
  "createdAt": "2026-07-14T18:30:00",
  "updatedAt": "2026-07-14T18:30:00"
}
```

---

## 8. Posts API

Типы постов:

```text
TEXT, WORKOUT, RECIPE, ACHIEVEMENT
```

Публичные посты создаются только с `visibility = PUBLIC`. Текстовый пост сообщества создаётся через Communities API и получает `visibility = COMMUNITY`.

Одна тренировка, один рецепт или одно пользовательское достижение могут быть опубликованы не более одного раза.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/posts/feed?type=...` | публичная лента | `200 PostResponse[]` |
| GET | `/api/posts/me?type=...` | посты текущего пользователя | `200 PostResponse[]` |
| GET | `/api/posts/{id}` | получить публичный пост | `200 PostResponse` |
| POST | `/api/posts/text` | создать публичный текстовый пост | `201 PostResponse` |
| POST | `/api/posts/workouts/{id}/share` | опубликовать существующую тренировку | `201 PostResponse` |
| POST | `/api/posts/recipes/{id}/share` | опубликовать существующий рецепт | `201 PostResponse` |
| POST | `/api/posts/achievements/{id}/share` | опубликовать полученное достижение | `201 PostResponse` |
| POST | `/api/posts/workouts` | создать тренировку и пост | `201 PostResponse` |
| POST | `/api/posts/recipes` | создать рецепт и пост | `201 PostResponse` |
| DELETE | `/api/posts/{id}` | удалить собственный пост | `204` |

Параметр `type` необязателен. Допустимые значения: `TEXT`, `WORKOUT`, `RECIPE`, `ACHIEVEMENT`.

`GET /api/posts/{id}` возвращает только пост с `PUBLIC`-видимостью. Посты сообщества получают через `/api/communities/{id}/posts`.

### Общая шапка поста

```json
{
  "postTitle": "Сегодня сделал отличную тренировку"
}
```

`postTitle` обязателен, до 128 символов.

### POST `/api/posts/text`

```json
{
  "post": {
    "postTitle": "Как прошёл мой день"
  },
  "content": "Сегодня добавил тренировку и приготовил полезный ужин."
}
```

`content` обязателен, до 2000 символов.

### Публикация существующей сущности

Одинаковое тело используется для тренировки, рецепта и достижения:

```json
{
  "postTitle": "Делюсь результатом"
}
```

Примеры:

```http
POST /api/posts/workouts/{workoutId}/share
POST /api/posts/recipes/{recipeId}/share
POST /api/posts/achievements/{userAchievementId}/share
```

Сущность должна принадлежать текущему пользователю. Повторная публикация возвращает `409`.

### POST `/api/posts/workouts`

```json
{
  "post": {
    "postTitle": "Новая тренировка"
  },
  "workout": {
    "title": "Кардио",
    "type": "CARDIO",
    "description": "Интервальный бег",
    "durationMinutes": 35,
    "caloriesBurned": 420
  }
}
```

### POST `/api/posts/recipes`

```json
{
  "post": {
    "postTitle": "Полезный завтрак"
  },
  "recipe": {
    "title": "Овсянка с ягодами",
    "description": "Полезный завтрак",
    "ingredients": "Овсянка, молоко, ягоды",
    "cookingSteps": "Смешать и сварить",
    "calories": 350,
    "proteins": 12.5,
    "fats": 8.0,
    "carbohydrates": 55.0,
    "imageUrl": "https://example.org/oatmeal.jpg"
  }
}
```

### PostResponse

```json
{
  "id": "6b6c2f64-3a9b-4c37-b6d1-4e6b5e1a1111",
  "authorId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "username": "george",
  "communityId": null,
  "title": "Новая тренировка",
  "visibility": "PUBLIC",
  "workout": {},
  "recipe": null,
  "userAchievement": null,
  "type": "WORKOUT",
  "content": null,
  "createdAt": "2026-07-14T18:30:00",
  "updatedAt": "2026-07-14T18:30:00"
}
```

Заполняется только вложенное поле, соответствующее `type`.

---

## 9. Comments API

Комментарии доступны для `PUBLIC`- и `COMMUNITY`-постов.

Правила доступа:

- для `PUBLIC`-поста любой авторизованный пользователь может читать и создавать комментарии;
- для `COMMUNITY`-поста читать и создавать комментарии может участник соответствующего сообщества;
- удалить комментарий может автор комментария, автор поста или владелец сообщества;
- неучастник сообщества в текущей реализации получает `404`.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/posts/{postId}/comments` | комментарии поста | `200 CommentResponse[]` |
| POST | `/api/posts/{postId}/comments` | создать комментарий | `201 CommentResponse` |
| DELETE | `/api/posts/{postId}/comments/{commentId}` | удалить комментарий | `204` |

### POST `/api/posts/{postId}/comments`

```json
{
  "content": "Отличная идея!"
}
```

`content` обязателен, до 2000 символов.

### CommentResponse

```json
{
  "id": "8f21a2d5-4f12-45e1-89df-01f5a9b6d111",
  "postId": "0fd1a2f5-5a42-43cf-9c1f-9e8a72f8a222",
  "authorId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "username": "george",
  "content": "Отличная идея!",
  "createdAt": "2026-07-14T18:30:00"
}
```

---

## 10. Communities API

Создатель сообщества автоматически становится участником с ролью `OWNER`. Обычный вступивший пользователь получает роль `MEMBER`.

Редактировать и удалять сообщество может только владелец. Владелец не может выйти из собственного сообщества.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/communities` | все сообщества | `200 CommunityResponse[]` |
| GET | `/api/communities/me` | сообщества текущего пользователя | `200 CommunityResponse[]` |
| GET | `/api/communities/{id}` | одно сообщество | `200 CommunityResponse` |
| POST | `/api/communities` | создать сообщество | `201 CommunityResponse` |
| PATCH | `/api/communities/{id}` | обновить собственное сообщество | `200 CommunityResponse` |
| DELETE | `/api/communities/{id}` | удалить собственное сообщество | `204` |
| POST | `/api/communities/{id}/join` | вступить | `200 CommunityResponse` |
| DELETE | `/api/communities/{id}/leave` | выйти | `204` |
| GET | `/api/communities/{id}/members` | список участников | `200 CommunityMemberResponse[]` |
| GET | `/api/communities/{id}/posts` | посты сообщества | `200 PostResponse[]` |
| POST | `/api/communities/{id}/posts/text` | создать текстовый пост сообщества | `201 PostResponse` |

### POST `/api/communities`

```json
{
  "communityName": "Бег по утрам",
  "description": "Сообщество для любителей утренних пробежек"
}
```

- `communityName`: обязательно, до 64 символов, уникально без учёта регистра;
- `description`: необязательно, до 1024 символов.

### PATCH `/api/communities/{id}`

```json
{
  "communityName": "Утренний бег",
  "description": "Обновлённое описание"
}
```

Все поля необязательны.

### POST `/api/communities/{id}/posts/text`

Создавать пост может только участник сообщества.

```json
{
  "title": "Кто завтра идёт на пробежку?",
  "content": "Стартуем в 8:00 у главного входа."
}
```

`title` — до 128 символов, `content` — до 2000 символов.

### CommunityResponse

```json
{
  "id": "b3d61bb8-1d52-47c9-a9c2-fd33f65f58b2",
  "ownerId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "communityName": "Бег по утрам",
  "description": "Сообщество для любителей бега",
  "membersCount": 15,
  "currentUserMember": true,
  "createdAt": "2026-07-14T18:30:00",
  "updatedAt": "2026-07-14T18:30:00"
}
```

### CommunityMemberResponse

```json
{
  "id": "a6a9b4c2-2c36-4b7d-8d6a-4bfae26f6d90",
  "communityId": "b3d61bb8-1d52-47c9-a9c2-fd33f65f58b2",
  "userId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "email": "user@example.com",
  "username": "george",
  "role": "MEMBER",
  "joinedAt": "2026-07-14T18:30:00"
}
```

Текущее поведение реализации: списки участников и постов сообщества требуют JWT, но отдельно не проверяют членство. Для комментариев community-поста членство проверяется.

---

## 11. Direct Chats API

Личный чат связывает двух разных пользователей. Для одной неупорядоченной пары пользователей существует не более одного чата.

Историю и отправку сообщений разрешено выполнять только участникам чата. Для постороннего пользователя возвращается `404`.

| Метод | Endpoint | Описание | Успех |
|---|---|---|---|
| GET | `/api/direct-chats` | чаты текущего пользователя | `200 DirectChatResponse[]` |
| POST | `/api/direct-chats` | создать или получить чат | `201 DirectChatResponse` |
| GET | `/api/direct-chats/{id}/messages` | история сообщений | `200 DirectMessageResponse[]` |
| POST | `/api/direct-chats/{id}/messages` | отправить сообщение | `201 DirectMessageResponse` |

Текущий контроллер возвращает `201` и при создании, и при возврате уже существующего чата.

### POST `/api/direct-chats`

```json
{
  "recipientId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478"
}
```

Создание чата с самим собой возвращает `400`.

### POST `/api/direct-chats/{id}/messages`

```json
{
  "content": "Привет! Как прошла тренировка?"
}
```

`content` обязателен, до 2000 символов.

### DirectChatResponse

```json
{
  "id": "0a9f0b33-54b7-4bc2-b4b9-3f8f9c5a2222",
  "companionId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "companionEmail": "user@example.com",
  "companionUsername": "george",
  "lastMessage": null,
  "createdAt": "2026-07-14T18:30:00"
}
```

### DirectMessageResponse

```json
{
  "id": "9e8a8db2-41a9-4f2f-a1d7-75a5d441b111",
  "chatId": "0a9f0b33-54b7-4bc2-b4b9-3f8f9c5a2222",
  "senderId": "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478",
  "senderUsername": "george",
  "content": "Привет! Как прошла тренировка?",
  "sentAt": "2026-07-14T18:31:00"
}
```

---

## 12. Текущие ограничения API

В текущей версии:

- пагинация отсутствует;
- редактирование постов отсутствует;
- групповой чат внутри сообщества отсутствует;
- real-time доставка сообщений через WebSocket отсутствует;
- отдельные административные endpoint'ы отсутствуют;
- для списков участников и постов сообщества пока проверяется только JWT, но не членство;
- `POST /api/direct-chats` всегда возвращает `201`, даже когда чат уже существовал.

Swagger является исполняемой документацией схем запросов и ответов:

```text
http://localhost:8080/swagger-ui.html
```
