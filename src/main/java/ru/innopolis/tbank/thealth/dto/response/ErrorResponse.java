package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Стандартный ответ ошибки")
public record ErrorResponse(
        @Schema(example = "404")
        int status,

        @Schema(example = "Workout not found by id ...")
        String message,

        @Schema(example = "/api/workouts/...")
        String path,

        LocalDateTime errorTime,

        Map<String, String> validationErrors
) {
}


