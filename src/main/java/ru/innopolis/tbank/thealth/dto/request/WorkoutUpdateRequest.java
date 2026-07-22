package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

@Schema(description = "Запрос на частичное обновление тренировки")
public record WorkoutUpdateRequest(

        @Schema(description = "Название тренировки", example = "Силовая тренировка")
        @Size(max = 128)
        @Pattern(regexp = "(?s).*\\S.*", message = "Workout title must not be blank")
        String title,

        @Schema(description = "Тип тренировки", example = "STRENGTH")
        WorkoutType type,

        @Schema(description = "Описание тренировки", example = "Жим, приседания, тяга")
        @Size(max = 2000)
        String description,

        @Schema(description = "Длительность тренировки в минутах", example = "45")
        @Positive
        Integer durationMinutes,

        @Schema(description = "Сожженные калории", example = "320")
        @PositiveOrZero
        Integer caloriesBurned
) {
}
