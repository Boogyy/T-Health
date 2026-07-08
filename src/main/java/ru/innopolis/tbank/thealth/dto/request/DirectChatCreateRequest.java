package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Запрос на создание личного чата")
public record DirectChatCreateRequest(

        @Schema(
                description = "Идентификатор пользователя, с которым нужно создать личный чат",
                example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478"
        )
        @NotNull
        UUID recipientId
) {
}