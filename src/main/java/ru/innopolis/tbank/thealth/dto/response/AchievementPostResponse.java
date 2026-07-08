package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.innopolis.tbank.thealth.enums.UserRole;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о тренировке")
public record AchievementPostResponse(

        @Schema(description = "Идентификатор тренировки", example = "6b6c2f64-3a9b-4c37-b6d1-4e6b5e1a1111")
        UUID id,

        @Schema(description = "Идентификатор владельца тренировки из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID userId,

        @Schema(description = "Название тренировки", example = "Силовая тренировка")
        String title,

        @Schema(description = "Тип тренировки", example = "STRENGTH")
        WorkoutType type,

        @Schema(description = "Описание тренировки", example = "Жим, приседания, тяга")
        String description,

        @Schema(description = "Длительность тренировки в минутах", example = "45")
        Integer durationMinutes,

        @Schema(description = "Сожженные калории", example = "320")
        Integer caloriesBurned,

        @Schema(description = "Дата тренировки", example = "2026-06-26T15:30:00")
        LocalDateTime workoutDate
) {
}
