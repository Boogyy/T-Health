package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Публичный профиль пользователя")
public record PublicUserProfileResponse(

        @Schema(description = "Внутренний идентификатор пользователя")
        UUID userId,

        @Schema(description = "Username пользователя")
        String username,

        @Schema(description = "Имя пользователя")
        String firstName,

        @Schema(description = "Фамилия пользователя")
        String lastName,

        @Schema(description = "Дата создания локального профиля")
        LocalDateTime memberSince,

        @Schema(description = "Публичные публикации пользователя")
        List<PostResponse> publications,

        @Schema(description = "Сообщества, в которых состоит пользователь")
        List<PublicCommunityResponse> communities
) {
}