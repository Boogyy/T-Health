package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о личном чате")
public record DirectChatResponse(

        @Schema(description = "Идентификатор личного чата", example = "0a9f0b33-54b7-4bc2-b4b9-3f8f9c5a2222")
        UUID id,

        @Schema(description = "Идентификатор собеседника из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID companionId,

        @Schema(description = "Email собеседника", example = "ivan@example.com")
        String companionEmail,

        @Schema(description = "Username собеседника", example = "ivan")
        String companionUsername,

        @Schema(description = "Последнее сообщение в чате")
        DirectMessageResponse lastMessage,

        @Schema(description = "Дата создания личного чата", example = "2026-07-08T15:00:00")
        LocalDateTime createdAt
) {
}