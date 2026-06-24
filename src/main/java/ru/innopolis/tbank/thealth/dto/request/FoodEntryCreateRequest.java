package ru.innopolis.tbank.thealth.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodEntryCreateRequest(

        @NotBlank
        @Size(max = 128)
        String mealName,

        @NotNull
        @PositiveOrZero
        Integer calories,

        @NotNull
        @PositiveOrZero
        BigDecimal proteins,

        @NotNull
        @PositiveOrZero
        BigDecimal fats,

        @NotNull
        @PositiveOrZero
        BigDecimal carbohydrates,

        LocalDateTime mealDate
) {
}