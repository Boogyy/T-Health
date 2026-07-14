package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание комментария")
public record CommentCreateRequest(

        @Schema(description = "Текст комментария", example = "Отличная идея, я тоже хочу присоединиться!")
        @NotBlank
        @Size(max = 2000)
        String content
) {
}