
# ER-диаграмма базы данных

## Описание

База данных проекта T-Health хранит информацию о пользователях, постах, комментариях, тренировках, питании, достижениях, сообществах и чатах.

## Основные сущности

-   `users` - пользователи
    
-   `posts` - посты в ленте
    
-   `comments` - комментарии к постам
    
-   `workouts` - тренировки пользователей
    
-   `food_entries` - записи о питании
    
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
    users ||--o{ user_achievements : receives
    users ||--o{ communities : owns
    users ||--o{ community_members : joins
    users ||--o{ messages : sends

    posts ||--o{ comments : has

    achievements ||--o{ user_achievements : assigned

    communities ||--o{ community_members : contains
    communities ||--|| chats : has

    chats ||--o{ messages : contains

    users {
        uuid id PK
        varchar username
        varchar email
        varchar password_hash
        varchar first_name
        varchar last_name
        timestamp created_at
        timestamp updated_at
    }

    posts {
        uuid id PK
        uuid author_id FK
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
        text description
        integer duration_minutes
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

## Связи между таблицами

### users → posts

Один пользователь может создать много постов.

### users → comments

Один пользователь может оставить много комментариев.

### posts → comments

Один пост может иметь много комментариев.

### users → workouts

Один пользователь может добавить много тренировок.

### users → food_entries

Один пользователь может добавить много записей о питании.

### users → user_achievements

Один пользователь может получить много достижений.

### achievements → user_achievements

Одно достижение может быть выдано многим пользователям.

### users → communities

Один пользователь может создать много сообществ.

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
    
-   индексы на внешние ключи:
    
    -   `posts.author_id`;
        
    -   `comments.post_id`;
        
    -   `comments.author_id`;
        
    -   `workouts.user_id`;
        
    -   `food_entries.user_id`;
        
    -   `messages.chat_id`;
        
    -   `messages.sender_id`.
        

## Типы связей

| Связь  | Тип |
| ------------- | ------------- |
| User - Post  | One-to-Many  |
| User - Comment  | One-to-Many  |
| Post - Comment  | One-to-Many  |
| User - Workout  | One-to-Many  |
| User - FoodEntry  | One-to-Many  |
| User - Achievement  | Many-to-Many через `user_achievements`  |
| User - Community  | Many-to-Many через `community_members`  |
| Community - Chat  | One-to-One  |
| Chat - Message  | One-to-Many  |
| User - Message  | One-to-Many  |
