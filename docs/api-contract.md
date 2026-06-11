# API-контракт

## Общие правила

- Базовый префикс API: `/api`.

- Все endpoint’ы, кроме `/auth/register` и `/auth/login`, требуют JWT-токен:

      `Authorization: Bearer <accessToken>`

- все идентификаторы ресурсов имеют тип UUID;

- даты передаются в формате ISO 8601;

- ошибки возвращаются в едином формате;

- посты в ленте различаются по полю `type`: `TEXT`, `WORKOUT`, `RECIPE`, `ACHIEVEMENT`;

- списки возвращаются с пагинацией через `page` и `size`;


По умолчанию:
- `page = 0`;
- `size = 20`.


## Auth API

| Метод | Endpoint       | Описание                 |
| ----- | -------------- | ------------------------ |
| POST  | /auth/register | Регистрация пользователя |
| POST  | /auth/login    | Авторизация пользователя |

### POST /auth/register

Request:

```json
{
  "username": "ivan",
  "email": "ivan@example.com",
  "password": "Password123",
  "firstName": "Иван",
  "lastName": "Иванов"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "username": "ivan",
  "email": "ivan@example.com",
  "role": "USER"
}
```

### POST /auth/login

Request:

```json
{
  "email": "ivan@example.com",
  "password": "Password123"
}
```

Response `200 OK`:

```json
{
  "accessToken": "jwt-token",
  "tokenType": "Bearer"
}
```

## Users API

| Метод | Endpoint  | Описание                                |
| ----- | --------- | --------------------------------------- |
| GET   | /users/me | Получение профиля текущего пользователя |
| PATCH | /users/me | Редактирование профиля                  |

### GET /users/me

Response `200 OK`:

```json
{
  "id": "uuid",
  "username": "ivan",
  "email": "ivan@example.com",
  "firstName": "Иван",
  "lastName": "Иванов",
  "role": "USER"
}
```

## Posts API

Пост — это публичная публикация в ленте активности.

Пост может быть одного из типов:

* `TEXT` — обычный текстовый пост;
* `WORKOUT` — пост с тренировкой;
* `RECIPE` — пост с рецептом;
* `ACHIEVEMENT` — пост с достижением.

| Метод  | Endpoint    | Описание                          |
| ------ | ----------- | --------------------------------- |
| GET    | /posts      | Получение ленты постов            |
| POST   | /posts      | Создание поста                    |
| GET    | /posts/{id} | Получение поста по идентификатору |
| PATCH  | /posts/{id} | Редактирование поста              |
| DELETE | /posts/{id} | Удаление поста                    |

### GET /posts

Query parameters:

| Параметр    | Описание                                                 |
| ----------- | -------------------------------------------------------- |
| page        | Номер страницы                                           |
| size        | Размер страницы                                          |
| type        | Фильтр по типу поста: TEXT, WORKOUT, RECIPE, ACHIEVEMENT |
| communityId | Фильтр по сообществу                                     |

Пример:

```http
GET /posts?page=0&size=20&type=WORKOUT
```

Response `200 OK`:

```json
{
  "content": [
    {
      "id": "uuid",
      "type": "WORKOUT",
      "visibility": "PUBLIC",
      "title": "Утренняя пробежка",
      "content": "Сегодня пробежал 5 км",
      "author": {
        "id": "uuid",
        "username": "ivan"
      },
      "createdAt": "2026-06-08T12:00:00",
      "commentsCount": 3,
      "payload": {
        "workoutId": "uuid",
        "title": "Бег",
        "durationMinutes": 40,
        "caloriesBurned": 420
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### POST /posts

Request для обычного текстового поста:

```json
{
  "type": "TEXT",
  "visibility": "PUBLIC",
  "title": "Кто завтра на тренировку?",
  "content": "Предлагаю утром пробежку"
}
```

Request для публикации рецепта:

```json
{
  "type": "RECIPE",
  "visibility": "PUBLIC",
  "title": "Полезный завтрак",
  "content": "Делюсь рецептом овсянки",
  "recipeId": "uuid"
}
```

Request для публикации тренировки:
```json
{
  "type": "WORKOUT",
  "visibility": "PUBLIC",
  "title": "Утренняя пробежка",
  "content": "Сегодня пробежал 5 км",
  "workoutId": "uuid"
}
```

Request для публикации достижения:
```json
{
  "type": "ACHIEVEMENT",
  "visibility": "PUBLIC",
  "title": "Новое достижение",
  "content": "Получил достижение за первую тренировку",
  "userAchievementId": "uuid"
}
```

Правила:

* если `type = WORKOUT`, поле `workoutId` обязательно;
* если `type = RECIPE`, поле `recipeId` обязательно;
* если `type = ACHIEVEMENT`, поле `userAchievementId` обязательно;
* если `type = TEXT`, поля `workoutId`, `recipeId`, `userAchievementId` не передаются;
* если `visibility = COMMUNITY`, поле `communityId` обязательно;
* пользователь может публиковать только свои тренировки, рецепты и достижения.

Response `201 Created`:

```json
{
  "id": "uuid",
  "type": "WORKOUT",
  "visibility": "PUBLIC",
  "title": "Утренняя пробежка",
  "content": "Сегодня отлично побегал",
  "createdAt": "2026-06-08T12:00:00"
}
```



## Comments API

| Метод  | Endpoint                 | Описание                     |
| ------ | ------------------------ | ---------------------------- |
| GET    | /posts/{postId}/comments | Получение комментариев поста |
| POST   | /posts/{postId}/comments | Создание комментария         |
| DELETE | /comments/{id}           | Удаление комментария         |

### POST /posts/{postId}/comments

Request:

```json
{
  "content": "Отличная тренировка!"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "postId": "uuid",
  "author": {
    "id": "uuid",
    "username": "anna"
  },
  "content": "Отличная тренировка!",
  "createdAt": "2026-06-08T12:30:00"
}
```

## Workouts API

Тренировки являются личными записями пользователя. Они не попадают в ленту автоматически.

Для публикации тренировки в ленте используется `POST /posts` с `type = WORKOUT`.

| Метод  | Endpoint       | Описание                          |
| ------ | -------------- | --------------------------------- |
| GET    | /workouts      | Получение тренировок пользователя |
| POST   | /workouts      | Создание тренировки               |
| PATCH  | /workouts/{id} | Редактирование тренировки         |
| DELETE | /workouts/{id} | Удаление тренировки               |

### POST /workouts

Request:

```json
{
  "title": "Утренняя пробежка",
  "type": "RUNNING",
  "description": "Бег в парке",
  "durationMinutes": 40,
  "caloriesBurned": 420,
  "workoutDate": "2026-06-08T08:00:00"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "title": "Утренняя пробежка",
  "type": "RUNNING",
  "durationMinutes": 40,
  "caloriesBurned": 420,
  "workoutDate": "2026-06-08T08:00:00"
}
```

## Food Entries API

Записи о питании являются личными записями пользователя и не отображаются в ленте активности.

| Метод  | Endpoint           | Описание                    |
| ------ | ------------------ | --------------------------- |
| GET    | /food-entries      | Получение записей о питании |
| POST   | /food-entries      | Создание записи о питании   |
| PATCH  | /food-entries/{id} | Редактирование записи       |
| DELETE | /food-entries/{id} | Удаление записи             |

### POST /food-entries

Request:

```json
{
  "mealName": "Овсянка с ягодами",
  "calories": 350,
  "proteins": 12,
  "fats": 8,
  "carbohydrates": 55,
  "mealDate": "2026-06-08T09:00:00"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "mealName": "Овсянка с ягодами",
  "calories": 350,
  "proteins": 12,
  "fats": 8,
  "carbohydrates": 55,
  "mealDate": "2026-06-08T09:00:00"
}
```

## Recipes API

Рецепт может быть личным или опубликованным в ленте через пост.

Для публикации рецепта используется `POST /posts` с `type = RECIPE`.

| Метод  | Endpoint      | Описание                                 |
| ------ | ------------- | ---------------------------------------- |
| GET    | /recipes      | Получение рецептов текущего пользователя |
| POST   | /recipes      | Создание рецепта                         |
| GET    | /recipes/{id} | Получение рецепта по идентификатору      |
| PATCH  | /recipes/{id} | Редактирование рецепта                   |
| DELETE | /recipes/{id} | Удаление рецепта                         |

### POST /recipes

Request:

```json
{
  "title": "Овсянка с ягодами",
  "description": "Полезный завтрак",
  "ingredients": "Овсянка, молоко, ягоды",
  "cookingSteps": "Сварить овсянку, добавить ягоды",
  "calories": 350,
  "proteins": 14,
  "fats": 8,
  "carbohydrates": 55,
  "imageUrl": "https://example.com/image.jpg"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "title": "Овсянка с ягодами",
  "description": "Полезный завтрак",
  "calories": 350,
  "proteins": 14,
  "fats": 8,
  "carbohydrates": 55,
  "createdAt": "2026-06-08T12:00:00"
}
```


## Achievements API

Для публикации достижения используется `POST /posts` с `type = ACHIEVEMENT` и `userAchievementId`.

| Метод | Endpoint               | Описание                          |
| ----- | ---------------------- | --------------------------------- |
| GET   | /achievements          | Получение списка достижений       |
| GET   | /users/me/achievements | Получение достижений пользователя |

## Communities API

| Метод  | Endpoint                | Описание                          |
| ------ | ----------------------- | --------------------------------- |
| GET    | /communities            | Получение списка сообществ        |
| POST   | /communities            | Создание сообщества               |
| GET    | /communities/{id}       | Получение информации о сообществе |
| POST   | /communities/{id}/join  | Вступление в сообщество           |
| DELETE | /communities/{id}/leave | Выход из сообщества               |

### POST /communities

Request:

```json
{
  "name": "Беговой клуб",
  "description": "Сообщество для любителей бега"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "name": "Беговой клуб",
  "description": "Сообщество для любителей бега",
  "ownerId": "uuid",
  "createdAt": "2026-06-08T12:00:00"
}
```


## Direct Chats API

Личные чаты предназначены для общения двух пользователей 1 на 1.
Пользователь может создать личный чат с любым зарегистрированным пользователем, кроме самого себя.
Если чат между пользователями уже существует, система возвращает существующий чат.

| Метод | Endpoint                        | Описание                                      |
| ----- | ------------------------------- | --------------------------------------------- |
| GET   | /direct-chats                   | Получение личных чатов текущего пользователя  |
| POST  | /direct-chats                   | Создание личного чата с другим пользователем  |
| GET   | /direct-chats/{chatId}/messages | Получение сообщений личного чата              |
| POST  | /direct-chats/{chatId}/messages | Отправка сообщения в личный чат               |

### POST /direct-chats

Request:

```json
{
  "participantId": "uuid"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "firstParticipant": {
    "id": "uuid",
    "username": "ivan"
  },
  "secondParticipant": {
    "id": "uuid",
    "username": "anna"
  },
  "createdAt": "2026-06-08T12:00:00"
}
```

### POST /direct-chats/{chatId}/messages

Request:

```json
{
  "content": "Привет!"
}
```

Response `201 Created`:

```json
{
  "id": "uuid",
  "chatId": "uuid",
  "sender": {
    "id": "uuid",
    "username": "ivan"
  },
  "content": "Привет!",
  "sentAt": "2026-06-08T12:30:00"
}
```




## Community Messages API

Сообщения сообщества доступны только участникам соответствующего сообщества.

| Метод | Endpoint                            | Описание                 |
| ----- | ----------------------------------- | ------------------------ |
| GET   | /communities/{communityId}/messages | Получение сообщений чата |
| POST  | /communities/{communityId}/messages | Отправка сообщения       |


### POST /communities/{communityId}/messages

Request:

```json
{
  "content": "Кто сегодня идёт на пробежку?"
}
```

Response 201 Created:
```json
{
  "id": "uuid",
  "communityId": "uuid",
  "sender": {
    "id": "uuid",
    "username": "ivan"
  },
  "content": "Кто сегодня идёт на пробежку?",
  "sentAt": "2026-06-08T12:30:00"
}
```

## Коды ответов

| Код | Описание                    |
| --- | --------------------------- |
| 200 | Успешный запрос             |
| 201 | Ресурс создан               |
| 204 | Успешное удаление           |
| 400 | Ошибка валидации            |
| 401 | Пользователь не авторизован |
| 403 | Доступ запрещен             |
| 404 | Ресурс не найден            |
| 409 | Конфликт данных             |
| 500 | Внутренняя ошибка сервера   |


## Формат ошибки

```json
{
  "timestamp": "2026-06-08T12:00:00",
  "status": 400,
  "error": "Validation error",
  "message": "Поле title не должно быть пустым",
  "path": "/posts"
}
```