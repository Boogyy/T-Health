# T-Health frontend

Отдельный frontend-проект на Vite. Внешний вид и текущая функциональность сохранены:

- регистрация через Keycloak;
- вход через Keycloak;
- callback с обменом authorization code на token через PKCE;
- вызов `GET /api/users/me` после входа/регистрации, чтобы backend создал локального пользователя;
- профиль пользователя;
- списки тренировок и приемов пищи;
- меню достижений в профиле и отдельная страница достижений;
- анимация получения достижений, которые реально пришли от backend;
- создание, просмотр, редактирование и удаление тренировок/приемов пищи;
- заглушка будущей ленты постов и заглушка кнопки «Поделиться».

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

## Как frontend ходит в backend

В коде API вызывается как текущий origin + `/api/**`.

В dev-режиме браузер делает запросы на:

```text
http://localhost:5173/api/...
```

А Vite проксирует их в backend:

```text
http://localhost:8089/api/...
```

Поэтому CORS для backend API в dev-режиме обычно не нужен.

## Настройки

Скопируйте `.env.example` в `.env`, если нужно поменять порты:

```bash
cp .env.example .env
```

Основные переменные:

```env
VITE_BACKEND_URL=http://localhost:8089
VITE_KEYCLOAK_BASE_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=t-health
VITE_KEYCLOAK_CLIENT_ID=t-health-frontend
```

`VITE_API_BASE_URL` лучше оставить пустым. Тогда работает same-origin режим через Vite proxy.

## Keycloak

Для клиента `t-health-frontend` добавьте:

```text
Valid redirect URIs: http://localhost:5173/*
Valid post logout redirect URIs: http://localhost:5173/*
Web origins: http://localhost:5173
```

Можно использовать `+` для post logout redirect/web origins, если это допустимо в вашей конфигурации.


## Достижения

Frontend получает достижения из backend endpoint:

```text
GET /api/users/me/achievements
```

Локальные демо-достижения больше не добавляются. Frontend не создает `Первый шаг в Т-Здоровье` и не дублирует backend-логику.

Сценарий работает так:

1. Пользователь нажимает `Register`.
2. После Keycloak callback frontend вызывает `GET /api/users/me`, чтобы backend создал локального пользователя.
3. Frontend запрашивает `GET /api/users/me/achievements`.
4. Если backend вернул одно или несколько новых достижений, например `Первая тренировка` и `Сжигатель ккал`, frontend показывает их по очереди в одном сценарии анимации.
5. После создания тренировки или приема пищи frontend заново запрашивает достижения и анимирует только те, которых не было до действия.
6. Кнопка `Принять` показывает следующее новое достижение, если их несколько; после последнего закрывает окно и возвращает пользователя в профиль.
7. Кнопка `Поделиться` пока показывает заглушку, потому что публикация в ленту будет подключаться позже.

В профиле добавлена третья карточка рядом с тренировками и питанием:

```text
Все достижения
```

Она ведет на страницу:

```text
#/achievements
```

Кнопка `Показать анимацию` убрана из общего списка достижений и доступна только на странице конкретного достижения: `#/achievements/{id}`. Локальные бейджи не создаются.

## Production-сборка

```bash
npm run build
```

Готовая статика появится в папке:

```text
dist/
```

Для production лучше отдавать `dist/` через nginx/CDN, а `/api/**` проксировать на backend.
