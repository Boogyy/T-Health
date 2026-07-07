package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о сообществе")
public record CommunityResponse(

        @Schema(description = "Идентификатор сообщества", example = "b3d61bb8-1d52-47c9-a9c2-fd33f65f58b2")
        UUID id,

        @Schema(description = "Идентификатор владельца сообщества из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID ownerId,

        @Schema(description = "Название сообщества", example = "Бег по утрам")
        String communityName,

        @Schema(description = "Описание сообщества", example = "Сообщество для тех, кто любит утренние пробежки")
        String description,

        @Schema(description = "Количество участников сообщества", example = "15")
        long membersCount,

        @Schema(description = "Является ли текущий пользователь участником сообщества", example = "true")
        boolean currentUserMember,

        @Schema(description = "Дата создания сообщества", example = "2026-07-07T12:30:00")
        LocalDateTime createdAt,

        @Schema(description = "Дата последнего обновления сообщества", example = "2026-07-07T12:45:00")
        LocalDateTime updatedAt
) {
}