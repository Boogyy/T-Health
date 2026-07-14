package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на отправку сообщения в личный чат")
public record DirectMessageCreateRequest(

        @Schema(
                description = "Текст сообщения",
                example = "Привет! Как прошла тренировка?"
        )
        @NotBlank
        @Size(max = 2000)
        String content
) {
}