package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание сообщества")
public record CommunityCreateRequest(

        @Schema(description = "Название сообщества", example = "Бег по утрам")
        @NotBlank
        @Size(max = 64)
        String communityName,

        @Schema(description = "Описание сообщества", example = "Сообщество для тех, кто любит утренние пробежки")
        @Size(max = 1024)
        String description
) {
}