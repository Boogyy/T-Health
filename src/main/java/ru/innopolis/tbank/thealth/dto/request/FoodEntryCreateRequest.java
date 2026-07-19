package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Запрос на создание записи питания")
public record FoodEntryCreateRequest(

        @Schema(description = "Название блюда", example = "Овсянка с ягодами")
        @NotBlank
        @Size(max = 128)
        String mealName,

        @Schema(description = "Калории", example = "350")
        @NotNull
        @PositiveOrZero
        Integer calories,

        @Schema(description = "Белки в граммах", example = "12.5")
        @NotNull
        @PositiveOrZero
        @Digits(integer = 4, fraction = 2)
        BigDecimal proteins,

        @Schema(description = "Жиры в граммах", example = "8.0")
        @NotNull
        @PositiveOrZero
        @Digits(integer = 4, fraction = 2)
        BigDecimal fats,

        @Schema(description = "Углеводы в граммах", example = "55.0")
        @NotNull
        @PositiveOrZero
        @Digits(integer = 4, fraction = 2)
        BigDecimal carbohydrates,

        @Schema(description = "Дата приема пищи", example = "2026-06-26T09:00:00")
        LocalDateTime mealDate
) {
}