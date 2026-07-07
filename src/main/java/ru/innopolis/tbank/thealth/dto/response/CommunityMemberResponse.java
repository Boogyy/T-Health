package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.innopolis.tbank.thealth.enums.CommunityRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация об участнике сообщества")
public record CommunityMemberResponse(

        @Schema(description = "Идентификатор записи участника", example = "a6a9b4c2-2c36-4b7d-8d6a-4bfae26f6d90")
        UUID id,

        @Schema(description = "Идентификатор сообщества", example = "b3d61bb8-1d52-47c9-a9c2-fd33f65f58b2")
        UUID communityId,

        @Schema(description = "Идентификатор пользователя из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID userId,

        @Schema(description = "Email пользователя", example = "user@example.com")
        String email,

        @Schema(description = "Username пользователя", example = "andrey")
        String username,

        @Schema(description = "Роль пользователя в сообществе", example = "MEMBER")
        CommunityRole role,

        @Schema(description = "Дата вступления в сообщество", example = "2026-07-07T12:45:00")
        LocalDateTime joinedAt
) {
}