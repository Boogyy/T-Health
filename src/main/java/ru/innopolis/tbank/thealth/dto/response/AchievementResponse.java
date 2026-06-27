package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о достижении")
public record AchievementResponse(

        @Schema(description = "Идентификатор достижения", example = "11111111-1111-1111-1111-111111111111")
        UUID id,

        @Schema(description = "Уникальный код достижения", example = "FIRST_FOOD_ENTRY")
        String code,

        @Schema(description = "Название достижения", example = "Первая запись питания")
        String title,

        @Schema(description = "Описание достижения", example = "Добавьте первую запись о приеме пищи")
        String description,

        @Schema(description = "Дата создания достижения", example = "2026-06-26T09:00:00")
        LocalDateTime createdAt
) {
}