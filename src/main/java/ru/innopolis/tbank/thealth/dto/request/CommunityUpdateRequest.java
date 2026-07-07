package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на обновление сообщества")
public record CommunityUpdateRequest(

        @Schema(description = "Название сообщества", example = "Бег по утрам")
        @Size(max = 64)
        String communityName,

        @Schema(description = "Описание сообщества", example = "Сообщество для тех, кто любит утренние пробежки")
        @Size(max = 1024)
        String description
) {
}