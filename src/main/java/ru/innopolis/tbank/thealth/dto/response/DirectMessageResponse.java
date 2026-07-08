package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о сообщении в личном чате")
public record DirectMessageResponse(

        @Schema(description = "Идентификатор сообщения", example = "9e8a8db2-41a9-4f2f-a1d7-75a5d441b111")
        UUID id,

        @Schema(description = "Идентификатор личного чата", example = "0a9f0b33-54b7-4bc2-b4b9-3f8f9c5a2222")
        UUID chatId,

        @Schema(description = "Идентификатор отправителя из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID senderId,

        @Schema(description = "Username отправителя", example = "andrey")
        String senderUsername,

        @Schema(description = "Текст сообщения", example = "Привет! Как прошла тренировка?")
        String content,

        @Schema(description = "Дата отправки сообщения", example = "2026-07-08T15:05:00")
        LocalDateTime sentAt
) {
}