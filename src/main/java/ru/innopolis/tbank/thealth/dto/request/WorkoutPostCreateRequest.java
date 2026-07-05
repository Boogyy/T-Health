package ru.innopolis.tbank.thealth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

@Schema(description = "Запрос на создание поста с тренировкой")
public record WorkoutPostCreateRequest(
        @Schema(description = "Надпись в шапке поста", example = "Сегодня сделал замечательную тренировку")
        @NotBlank
        @Size(max = 128)
        String postTitle,

        @Schema(description = "Название тренировки", example = "Силовая тренировка")
        @NotBlank
        @Size(max = 128)
        String title,

        @Schema(description = "Тип тренировки", example = "STRENGTH")
        @NotNull
        WorkoutType type,

        @Schema(description = "Описание тренировки", example = "Жим, приседания, тяга")
        @Size(max = 2000)
        String description,

        @Schema(description = "Длительность тренировки в минутах", example = "45")
        @NotNull
        @Positive
        Integer durationMinutes,

        @Schema(description = "Сожженные калории", example = "320")
        @PositiveOrZero
        Integer caloriesBurned
) {
}
