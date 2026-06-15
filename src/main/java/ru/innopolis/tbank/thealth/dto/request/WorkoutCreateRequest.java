package ru.innopolis.tbank.thealth.dto.request;


import jakarta.validation.constraints.*;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

public record WorkoutCreateRequest (
        @NotBlank
        @Size(max = 128)
        String title,

        @NotNull
        WorkoutType type,

        @Size(max = 2000)
        String description,

        @NotNull
        @Positive
        Integer durationMinutes,

        @PositiveOrZero
        Integer caloriesBurned
) {
}
