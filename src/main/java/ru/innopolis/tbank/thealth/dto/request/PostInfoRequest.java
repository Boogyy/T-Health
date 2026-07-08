package ru.innopolis.tbank.thealth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Запрос на создание поста с тренировкой")
public record PostInfoRequest(
        @Schema(description = "Надпись в шапке поста", example = "Сегодня сделал замечательную тренировку")
        @NotBlank
        @Size(max = 128)
        String postTitle
) {
}
