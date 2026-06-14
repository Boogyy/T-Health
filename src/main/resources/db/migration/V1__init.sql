CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    first_name VARCHAR(64),
    last_name VARCHAR(64),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_user_email UNIQUE (email),
    CONSTRAINT unique_username UNIQUE (username),
    CONSTRAINT check_user_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE IF NOT EXISTS achievements (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    title VARCHAR(64) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_achievement_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS communities (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    community_name VARCHAR(64) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX communities_owner_idx
ON communities(owner_id);

CREATE TABLE IF NOT EXISTS workouts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    description TEXT,
    duration_minutes INTEGER NOT NULL,
    calories_burned INTEGER,
    workout_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_workout_duration CHECK (duration_minutes > 0),
    CONSTRAINT check_workout_calories CHECK (calories_burned IS NULL OR calories_burned >= 0)
);

CREATE INDEX workouts_user_idx
ON workouts(user_id);

CREATE TABLE IF NOT EXISTS food_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    meal_name VARCHAR(128) NOT NULL,
    calories INTEGER NOT NULL,
    proteins DECIMAL(6,2) NOT NULL,
    fats DECIMAL(6,2) NOT NULL,
    carbohydrates DECIMAL(6,2) NOT NULL,
    meal_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_food_calories CHECK (calories >= 0),
    CONSTRAINT check_food_proteins CHECK (proteins >= 0),
    CONSTRAINT check_food_fats CHECK (fats >= 0),
    CONSTRAINT check_food_carbohydrates CHECK (carbohydrates >= 0)
);

CREATE INDEX food_entries_user_idx
ON food_entries(user_id);

CREATE TABLE IF NOT EXISTS recipes (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    ingredients TEXT NOT NULL,
    cooking_steps TEXT NOT NULL,
    calories INTEGER,
    proteins DECIMAL(6,2),
    fats DECIMAL(6,2),
    carbohydrates DECIMAL(6,2),
    image_url VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_recipe_calories CHECK (calories IS NULL OR calories >= 0),
    CONSTRAINT check_recipe_proteins CHECK (proteins IS NULL OR proteins >= 0),
    CONSTRAINT check_recipe_fats CHECK (fats IS NULL OR fats >= 0),
    CONSTRAINT check_recipe_carbohydrates CHECK (carbohydrates IS NULL OR carbohydrates >= 0)
);

CREATE INDEX recipes_author_idx
ON recipes(author_id);

CREATE TABLE IF NOT EXISTS user_achievements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    achievement_id UUID NOT NULL REFERENCES achievements(id),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_user_achievement UNIQUE (user_id, achievement_id)
);

CREATE INDEX user_achievements_user_idx
ON user_achievements(user_id);

CREATE INDEX user_achievements_achievement_idx
ON user_achievements(achievement_id);

CREATE TABLE IF NOT EXISTS community_members (
    id UUID PRIMARY KEY,
    community_id UUID NOT NULL REFERENCES communities(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_community_member UNIQUE (community_id, user_id),
    CONSTRAINT check_community_member_role
    CHECK (role IN ('OWNER', 'MODERATOR', 'MEMBER'))
);

CREATE INDEX community_members_community_idx
ON community_members(community_id);

CREATE INDEX community_members_user_idx
ON community_members(user_id);


CREATE TABLE IF NOT EXISTS posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users(id),
    community_id UUID REFERENCES communities(id),
    visibility VARCHAR(32) NOT NULL,
    workout_id UUID REFERENCES workouts(id),
    recipe_id UUID REFERENCES recipes(id),
    user_achievement_id UUID REFERENCES user_achievements(id),
    type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_post_type
    CHECK (type IN ('TEXT', 'WORKOUT', 'RECIPE', 'ACHIEVEMENT')),

    CONSTRAINT check_post_visibility
    CHECK (visibility IN ('PUBLIC', 'COMMUNITY'))
);

CREATE UNIQUE INDEX unique_workout_post_idx
ON posts(workout_id)
WHERE workout_id IS NOT NULL;

CREATE UNIQUE INDEX unique_recipe_post_idx
ON posts(recipe_id)
WHERE recipe_id IS NOT NULL;

CREATE UNIQUE INDEX unique_user_achievement_post_idx
ON posts(user_achievement_id)
WHERE user_achievement_id IS NOT NULL;

CREATE INDEX posts_created_at_idx
ON posts(created_at DESC);

CREATE INDEX posts_author_idx
ON posts(author_id);

CREATE INDEX posts_community_idx
ON posts(community_id);



CREATE TABLE IF NOT EXISTS comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id),
    author_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX comments_sorted_idx
ON comments(post_id, created_at DESC);

CREATE INDEX comments_user_idx
ON comments(author_id);


CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    community_id UUID NOT NULL REFERENCES communities(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX messages_sorted_idx
ON messages(community_id, sent_at DESC);

CREATE INDEX messages_sender_idx
ON messages(sender_id);

CREATE TABLE IF NOT EXISTS direct_chats (
    id UUID PRIMARY KEY,
    first_user_id UUID NOT NULL REFERENCES users(id),
    second_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_direct_chat_users_different
    CHECK (first_user_id <> second_user_id)
);

CREATE UNIQUE INDEX unique_direct_chat_pair_idx
ON direct_chats (
    LEAST(first_user_id, second_user_id),
    GREATEST(first_user_id, second_user_id)
);

CREATE TABLE IF NOT EXISTS direct_messages (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES direct_chats(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX direct_messages_sorted_idx
    ON direct_messages(chat_id, sent_at DESC);

CREATE INDEX direct_messages_sender_idx
    ON direct_messages(sender_id);
