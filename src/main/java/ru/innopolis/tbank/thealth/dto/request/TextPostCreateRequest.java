package ru.innopolis.tbank.thealth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

@Schema(description = "Запрос на создание текстового поста")
public record TextPostCreateRequest(
        @Schema(description = "Надпись в шапке поста", example = "История о том как прошел день")
        @NotNull
        @Valid
        PostInfoRequest post,

        @Schema(description = "Непосредственно содержание поста")
        @NotBlank
        @Size(max = 2000)
        String content
) {
}
