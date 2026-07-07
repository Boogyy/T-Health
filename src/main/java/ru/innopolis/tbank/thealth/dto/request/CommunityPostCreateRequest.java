package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание текстового поста в сообществе")
public record CommunityPostCreateRequest(

        @Schema(description = "Заголовок поста", example = "Кто завтра идет на пробежку?")
        @NotBlank
        @Size(max = 128)
        String title,

        @Schema(description = "Текст поста", example = "Планирую пробежку в 8:00, кто хочет присоединиться?")
        @NotBlank
        @Size(max = 2000)
        String content
) {
}