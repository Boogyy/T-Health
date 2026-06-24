package ru.innopolis.tbank.thealth.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodEntryUpdateRequest(

        @Size(max = 128)
        String mealName,

        @PositiveOrZero
        Integer calories,

        @PositiveOrZero
        BigDecimal proteins,

        @PositiveOrZero
        BigDecimal fats,

        @PositiveOrZero
        BigDecimal carbohydrates,

        LocalDateTime mealDate
) {
}