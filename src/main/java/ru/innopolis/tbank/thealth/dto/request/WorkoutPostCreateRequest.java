package ru.innopolis.tbank.thealth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

@Schema(description = "Запрос на создание поста с тренировкой")
public record WorkoutPostCreateRequest(
        @Schema(description = "Надпись в шапке поста", example = "Сегодня сделал замечательную тренировку")
        @NotNull
        @Valid
        PostInfoRequest post,

        @Schema(description = "Данные создаваемой тренировки")
        @NotNull
        @Valid
        WorkoutCreateRequest workout
) {
}
