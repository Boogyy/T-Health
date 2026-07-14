package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о комментарии")
public record CommentResponse(

        @Schema(description = "Идентификатор комментария", example = "8f21a2d5-4f12-45e1-89df-01f5a9b6d111")
        UUID id,

        @Schema(description = "Идентификатор поста", example = "0fd1a2f5-5a42-43cf-9c1f-9e8a72f8a222")
        UUID postId,

        @Schema(description = "Идентификатор автора комментария из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID authorId,

        @Schema(description = "Username автора комментария", example = "andrey")
        String username,

        @Schema(description = "Текст комментария", example = "Отличная идея, я тоже хочу присоединиться!")
        String content,

        @Schema(description = "Дата создания комментария", example = "2026-07-07T13:55:00")
        LocalDateTime createdAt
) {
}