# T-Health frontend

Отдельный frontend-проект на Vite под текущие backend endpoint'ы T-Health.

Реализовано:

- регистрация и вход через Keycloak;
- callback с обменом authorization code на token через PKCE;
- вызов `GET /api/users/me` после входа/регистрации для создания/получения локального пользователя;
- профиль пользователя;
- личные тренировки;
- личные `food_entries` с дневником КБЖУ по выбранной дате;
- отдельные рецепты, которыми можно делиться в ленте;
- достижения пользователя и анимация новых достижений;
- публичная лента постов;
- фильтрация ленты через выпадающее меню с выбором одного или нескольких типов `TEXT`, `WORKOUT`, `RECIPE`, `ACHIEVEMENT`;
- создание текстового поста;
- создание поста-тренировки;
- создание поста-рецепта;
- публикация существующей тренировки, рецепта или достижения через кнопку `Поделиться`.

## Запуск

```bash
npm install
npm run dev
```

Frontend будет доступен здесь:

```text
http://localhost:5173/
```

Backend по умолчанию ожидается здесь:

```text
http://localhost:8089/
```

Keycloak по умолчанию ожидается здесь:

```text
http://localhost:8180/
```

## Настройки

Скопируйте `.env.example` в `.env`:

```bash
cp .env.example .env
```

Базовые значения:

```env
VITE_BACKEND_URL=http://localhost:8089
VITE_KEYCLOAK_BASE_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=t-health
VITE_KEYCLOAK_CLIENT_ID=t-health-frontend
```

`VITE_BACKEND_URL` используется в `vite.config.js` для proxy. В коде frontend API вызывается через текущий origin + `/api/**`.

В dev-режиме браузер обращается к:

```text
http://localhost:5173/api/...
```

а Vite проксирует запросы в backend:

```text
http://localhost:8089/api/...
```

Поэтому CORS для backend API в dev-режиме обычно не нужен.

## Keycloak

Для клиента `t-health-frontend` должны быть разрешены:

```text
Valid redirect URIs: http://localhost:5173/*
Valid post logout redirect URIs: http://localhost:5173/*
Web origins: http://localhost:5173
```

## Используемые backend endpoint'ы

Профиль и auth flow:

```text
GET /api/users/me
GET /api/users/me/achievements
```

Тренировки:

```text
GET    /api/workouts
POST   /api/workouts
GET    /api/workouts/{id}
PATCH  /api/workouts/{id}
DELETE /api/workouts/{id}
```

Food entries:

```text
GET    /api/food-entries
GET    /api/food-entries/daily?date=YYYY-MM-DD
POST   /api/food-entries
GET    /api/food-entries/{id}
PATCH  /api/food-entries/{id}
DELETE /api/food-entries/{id}
```

Рецепты:

```text
GET    /api/recipes
POST   /api/recipes
GET    /api/recipes/{id}
PATCH  /api/recipes/{id}
DELETE /api/recipes/{id}
```

Посты:

```text
GET  /api/posts/feed
GET  /api/posts/feed?type=TEXT
GET  /api/posts/feed?type=WORKOUT
GET  /api/posts/feed?type=RECIPE
GET  /api/posts/feed?type=ACHIEVEMENT

При выборе нескольких типов frontend делает несколько запросов с одиночным `type`, объединяет результаты и сортирует их по дате создания.
POST /api/posts/text
POST /api/posts/workouts
POST /api/posts/recipes
POST /api/posts/workouts/{id}/share
POST /api/posts/recipes/{id}/share
POST /api/posts/achievements/{id}/share
```

## Важная логика

`food_entries` остаются личным дневником КБЖУ и не публикуются в ленте.

`recipes` являются отдельным разделом: их можно хранить в профиле и публиковать в ленте.

Кнопка `Поделиться` есть у тренировок, рецептов и достижений. У `food_entries` такой кнопки нет.

Если backend выдает несколько новых достижений за одно действие, frontend показывает анимации последовательно.
