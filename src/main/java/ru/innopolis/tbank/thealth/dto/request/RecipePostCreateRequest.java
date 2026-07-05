package ru.innopolis.tbank.thealth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

@Schema(description = "Запрос на создание поста с рецептом")
public record RecipePostCreateRequest(
        @Schema(description = "Надпись в шапке поста", example = "Сегодня приготовил замечательную яичницу")
        @NotNull
        @Valid
        PostInfoRequest post,

        @Schema(description = "Данные создаваемого рецепта")
        @NotNull
        @Valid
        RecipeCreateRequest recipe
) {
}
