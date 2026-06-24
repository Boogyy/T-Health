package ru.innopolis.tbank.thealth.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FoodEntryResponse(
        UUID id,
        UUID userId,
        String mealName,
        Integer calories,
        BigDecimal proteins,
        BigDecimal fats,
        BigDecimal carbohydrates,
        LocalDateTime mealDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}