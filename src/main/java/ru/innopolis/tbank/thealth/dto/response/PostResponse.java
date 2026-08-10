package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.enums.UserRole;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о посте")
public record PostResponse(
        @Schema(description = "Идентификатор поста", example = "6b6c2f64-3a9b-4c37-b6d1-4e6b5e1a1111")
        UUID id,

        @Schema(description = "Идентификатор автора поста из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID authorId,

        @Schema(description = "Имя пользователя")
        String username,

        @Schema(description = "Идентификатор сообщества", example = "ccd11ba4-82f7-42cb-82f73a88-19d9e4fdb478")
        UUID communityId,

        @Schema(description = "Надпись в шапке поста", example = "Сегодня сделал замечательную тренировку")
        String title,

        @Schema(description = "Доступность поста")
        PostVisibility visibility,

        @Schema(description = "Информация о тренировке", example = "id, description, etc...")
        WorkoutResponse workout,

        @Schema(description = "Информация о рецепте", example = "id, description, etc...")
        RecipeResponse recipe,

        @Schema(description = "Информация о достижении", example = "id, description, etc...")
        UserAchievementResponse userAchievement,

        @Schema(description = "Тип поста", example = "WORKOUT")
        PostType type,

        @Schema(description = "Данные текстового поста", example = "Всем привет, посоветуйте упражнения для кора")
        String content,

        @Schema(description = "Количество комментариев к посту", example = "7")
        long commentsCount,

        @Schema(description = "Дата создания поста")
        LocalDateTime createdAt,

        @Schema(description = "Дата изменения поста")
        LocalDateTime updatedAt
) {
}

