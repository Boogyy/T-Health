package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.innopolis.tbank.thealth.enums.CommunityRole;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Сообщество в публичном профиле пользователя")
public record PublicCommunityResponse(

        @Schema(description = "Идентификатор сообщества")
        UUID id,

        @Schema(description = "Название сообщества")
        String communityName,

        @Schema(description = "Описание сообщества")
        String description,

        @Schema(description = "Роль пользователя в сообществе")
        CommunityRole role,

        @Schema(description = "Дата вступления пользователя в сообщество")
        LocalDateTime joinedAt
) {
}