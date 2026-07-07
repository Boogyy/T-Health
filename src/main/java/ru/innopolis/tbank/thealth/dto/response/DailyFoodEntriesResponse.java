package ru.innopolis.tbank.thealth.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyFoodEntriesResponse(
        LocalDate date,
        Integer totalCalories,
        BigDecimal totalProteins,
        BigDecimal totalFats,
        BigDecimal totalCarbohydrates,
        List<FoodEntryResponse> entries
) {
}
