package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о достижении пользователя")
public record UserAchievementResponse(

        @Schema(description = "Идентификатор записи о полученном достижении", example = "8a4a34d4-7a56-4ac3-a3a3-f0d7b6a99991")
        UUID id,

        @Schema(description = "Идентификатор пользователя из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID userId,

        @Schema(description = "Полученное достижение")
        AchievementResponse achievement,

        @Schema(description = "Дата получения достижения", example = "2026-06-26T09:15:00")
        LocalDateTime receivedAt
) {
}