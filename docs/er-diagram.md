
# ER-диаграмма базы данных

## Описание

База данных проекта T-Health хранит информацию о пользователях, постах в ленте активности, комментариях, тренировках, приемах пищи, 
рецептах, достижениях, сообществах и чатах.


В системе разделяются личные записи пользователя и публичные публикации в ленте:

- `food_entries` — личные записи о приёмах пищи пользователя. Они используются для отслеживания КБЖУ и по умолчанию не отображаются в ленте.
- `workouts` — личные записи о тренировках пользователя. Тренировка может оставаться личной или быть опубликована в ленте через пост.
- `recipes` — рецепты пользователя. Рецепт может быть создан для личного использования или опубликован в ленте через пост.
- `posts` — публичные публикации в ленте активности. Пост может быть обычным текстовым постом или ссылаться на тренировку, рецепт или достижение пользователя.

## Основные сущности

-   `users` - пользователи
    
-   `posts` - посты в ленте
    
-   `comments` - комментарии к постам
    
-   `workouts` - тренировки пользователей
    
-   `food_entries` - личные записи о приемах пищи

-   `recipes` - рецепты пользователей
    
-   `achievements` - справочник достижений
    
-   `user_achievements` - достижения пользователей
    
-   `communities` - сообщества по интересам
    
-   `community_members` - участники сообществ
    
-   `chats` - чаты сообществ
    
-   `messages` - сообщения в чатах
    

## ER-диаграмма

```mermaid
erDiagram
    users ||--o{ posts : creates
    users ||--o{ comments : writes
    users ||--o{ workouts : logs
    users ||--o{ food_entries : logs
    users ||--o{ recipes : creates
    users ||--o{ user_achievements : receives
    users ||--o{ communities : owns
    users ||--o{ community_members : joins
    users ||--o{ messages : sends

    posts ||--o{ comments : has
    
    workouts |o--o| posts : published_as
    recipes |o--o| posts : published_as
    user_achievements |o--o| posts : published_as

    achievements ||--o{ user_achievements : assigned

    communities ||--o{ community_members : contains
    communities ||--|| chats : has
    communities |o--o{ posts : contains

    chats ||--o{ messages : contains

    users {
        uuid id PK
        varchar username
        varchar email
        varchar password_hash
        varchar first_name
        varchar last_name
        varchar role
        timestamp created_at
        timestamp updated_at
    }

    posts {
        uuid id PK
        uuid author_id FK
        uuid community_id FK
        uuid workout_id FK
        uuid recipe_id FK
        uuid user_achievement_id FK
        varchar type
        varchar visibility
        varchar title
        text content
        timestamp created_at
        timestamp updated_at
    }

    comments {
        uuid id PK
        uuid post_id FK
        uuid author_id FK
        text content
        timestamp created_at
    }

    workouts {
        uuid id PK
        uuid user_id FK
        varchar title
        varchar type
        text description
        integer duration_minutes
        integer calories_burned
        timestamp workout_date
        timestamp created_at
        timestamp updated_at
    }

    food_entries {
        uuid id PK
        uuid user_id FK
        varchar meal_name
        integer calories
        decimal proteins
        decimal fats
        decimal carbohydrates
        timestamp meal_date
        timestamp created_at
        timestamp updated_at
    }
    
    recipes {
        uuid id PK
        uuid author_id FK
        varchar title
        text description
        text ingredients
        text cooking_steps
        integer calories
        decimal proteins
        decimal fats
        decimal carbohydrates
        varchar image_url
        timestamp created_at
        timestamp updated_at
    }

    achievements {
        uuid id PK
        varchar code
        varchar title
        text description
        timestamp created_at
    }

    user_achievements {
        uuid id PK
        uuid user_id FK
        uuid achievement_id FK
        timestamp received_at
    }

    communities {
        uuid id PK
        uuid owner_id FK
        varchar name
        text description
        timestamp created_at
        timestamp updated_at
    }

    community_members {
        uuid id PK
        uuid community_id FK
        uuid user_id FK
        varchar role
        timestamp joined_at
    }

    chats {
        uuid id PK
        uuid community_id FK
        timestamp created_at
    }

    messages {
        uuid id PK
        uuid chat_id FK
        uuid sender_id FK
        text content
        timestamp sent_at
    }

```

## Логика личных записей и публикаций

### Приёмы пищи

Приём пищи является личной записью пользователя и хранится в таблице `food_entries`.

Пользователь может добавить приём пищи для отслеживания дневного прогресса по КБЖУ. Такая запись видна только самому пользователю и не отображается в ленте активности.

Создание записи в `food_entries` не создаёт пост в `posts`.

### Рецепты

Рецепт хранится в таблице `recipes`.

Пользователь может создать рецепт только для себя. В этом случае создаётся запись в `recipes`, но пост в ленте не создаётся.

Если пользователь хочет поделиться рецептом, создаётся запись в `posts` с `type = RECIPE`, которая ссылается на рецепт через `recipe_id`.

### Тренировки

Тренировка хранится в таблице `workouts`.

Пользователь может создать тренировку только для себя. В этом случае создаётся запись в `workouts`, но пост в ленте не создаётся.

Если пользователь хочет поделиться тренировкой, создаётся запись в `posts` с `type = WORKOUT`, которая ссылается на тренировку через `workout_id`.

Если пользователь сразу создаёт тренировку как пост, система сначала создаёт запись в `workouts`, а затем создаёт запись в `posts` с ссылкой на эту тренировку.

### Посты

Пост является публичной публикацией в ленте активности.

Поле `type` определяет тип поста:

* `TEXT` - обычный текстовый пост;
* `WORKOUT` - пост с тренировкой;
* `RECIPE` - пост с рецептом;
* `ACHIEVEMENT` - пост с достижением.

Поле `visibility` определяет видимость поста:

* `PUBLIC` - пост доступен всем пользователям;
* `COMMUNITY` - пост доступен участникам конкретного сообщества.

Если `visibility = COMMUNITY`, поле `community_id` должно быть заполнено.

Если `visibility = PUBLIC`, поле `community_id` должно быть пустым.

Если `type = WORKOUT`, должно быть заполнено поле `workout_id`.

Если `type = RECIPE`, должно быть заполнено поле `recipe_id`.

Если `type = ACHIEVEMENT`, должно быть заполнено поле `user_achievement_id`.

Если `type = TEXT`, поля `workout_id`, `recipe_id` и `user_achievement_id` должны быть пустыми.

## Связи между таблицами

### users → posts

Один пользователь может создать много постов.

### users → comments

Один пользователь может оставить много комментариев.

### posts → comments

Один пост может иметь много комментариев.

### users → workouts

Один пользователь может добавить много тренировок.

### workouts → posts

Одна тренировка может быть опубликована как пост в ленте активности.

### users → food_entries

Один пользователь может добавить много личных записей о приемах пищи.

### users → recipes

Один пользователь может создать много рецептов.

### recipes → posts

Один рецепт может быть опубликован как пост в ленте активности.

### users → user_achievements

Один пользователь может получить много достижений.

### achievements → user_achievements

Одно достижение может быть выдано многим пользователям.

### user_achievements → posts

Полученное пользователем достижение может быть опубликовано как пост в ленте активности.

### users → communities

Один пользователь может создать много сообществ.

### communities → posts

Одно сообщество может содержать много постов.

### communities → community_members

Одно сообщество может иметь много участников.

### users → community_members

Один пользователь может состоять в нескольких сообществах.

### communities → chats

Одно сообщество имеет один чат.

### chats → messages

Один чат содержит много сообщений.

### users → messages

Один пользователь может отправить много сообщений.

## Индексы и ограничения

Для повышения производительности и целостности данных планируется использовать:

-   уникальный индекс на `users.email`;
    
-   уникальный индекс на `users.username`;
    
-   уникальный индекс на `achievements.code`;
    
-   уникальный индекс на пару `user_achievements(user_id, achievement_id)`;
    
-   уникальный индекс на пару `community_members(community_id, user_id)`;

-   уникальный индекс на `chats.community_id`, чтобы у одного сообщества был только один чат;
  
-   уникальный индекс на `posts.workout_id`, где `workout_id` IS NOT NULL; 

-   уникальный индекс на `posts.recipe_id`, где `recipe_id` IS NOT NULL;
  
-   уникальный индекс на `posts.user_achievement_id`, где `user_achievement_id` IS NOT NULL.

-   индекс на `posts.created_at` для ускорения сортировки ленты активности;

-   индекс на `messages.sent_at` для сортировки истории сообщений;

-   индекс на `comments.created_at` для сортировки комментариев.
    
-   индексы на внешние ключи:
    
    -   `posts.author_id`;
    
    -   `posts.community_id`;
    
    -   `posts.workout_id`;

    -   `posts.recipe_id`;
    
    -   `posts.user_achievement_id`;
        
    -   `comments.post_id`;
        
    -   `comments.author_id`;
        
    -   `workouts.user_id`;
        
    -   `food_entries.user_id`;

    -   `recipes.author_id`;
    
    -   `user_achievements.user_id`;
    
    -   `user_achievements.achievement_id`;
    
    -   `communities.owner_id`;
    
    -   `community_members.community_id`;
    
    -   `community_members.user_id`;
    
    -   `chats.community_id`;
        
    -   `messages.chat_id`;
        
    -   `messages.sender_id`.


## Типы связей

| Связь                      | Тип                                    |
| -------------------------- | -------------------------------------- |
| User - Post                | One-to-Many                            |
| User - Comment             | One-to-Many                            |
| Post - Comment             | One-to-Many                            |
| User - Workout             | One-to-Many                            |
| Workout - Post             | One-to-Zero-or-One                     |
| User - FoodEntry           | One-to-Many                            |
| User - Recipe              | One-to-Many                            |
| Recipe - Post              | One-to-Zero-or-One                     |
| User - Achievement         | Many-to-Many через `user_achievements` |
| UserAchievement - Post     | One-to-Zero-or-One                     |
| User - Community as owner  | One-to-Many                            |
| User - Community as member | Many-to-Many через `community_members` |
| Community - Post           | One-to-Many                            |
| Community - Chat           | One-to-One                             |
| Chat - Message             | One-to-Many                            |
| User - Message             | One-to-Many                            |
