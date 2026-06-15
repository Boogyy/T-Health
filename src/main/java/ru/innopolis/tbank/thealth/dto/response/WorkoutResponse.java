package ru.innopolis.tbank.thealth.dto.response;

import ru.innopolis.tbank.thealth.enums.UserRole;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

import java.util.UUID;

public record WorkoutResponse (
        UUID id,
        UUID userId,
        String title,
        WorkoutType type,
        String description,
        Integer durationMinutes,
        Integer caloriesBurned
) {
}
