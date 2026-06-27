package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Запрос на частичное обновление записи питания")
public record FoodEntryUpdateRequest(

        @Schema(description = "Название блюда", example = "Овсянка с ягодами")
        @Size(max = 128)
        String mealName,

        @Schema(description = "Калории", example = "350")
        @PositiveOrZero
        Integer calories,

        @Schema(description = "Белки в граммах", example = "12.5")
        @PositiveOrZero
        BigDecimal proteins,

        @Schema(description = "Жиры в граммах", example = "8.0")
        @PositiveOrZero
        BigDecimal fats,

        @Schema(description = "Углеводы в граммах", example = "55.0")
        @PositiveOrZero
        BigDecimal carbohydrates,

        @Schema(description = "Дата приема пищи", example = "2026-06-26T09:00:00")
        LocalDateTime mealDate
) {
}