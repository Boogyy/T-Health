package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecipeCreateRequest (
        @Schema(description = "Название рецепта", example = "Овсянка с ягодами")
        @NotBlank
        @Size(max = 128)
        String title,

        @Schema(description = "Описание", example = "Высокоуглеводный завтрак")
        @NotBlank
        @Size(max = 512)
        String description,


        @Schema(description = "Ингредиенты для блюда", example = "Овсяные хлопья, клубника")
        @NotBlank
        @Size(max = 2000)
        String ingredients,

        @Schema(description = "Шаги приготовления", example = "Залить овсянку в соотношении 1 к 3...")
        @NotBlank
        @Size(max = 4000)
        String cookingSteps,

        @Schema(description = "Калории", example = "350")
        @PositiveOrZero
        Integer calories,

        @Schema(description = "Белки в граммах", example = "12.5")
        @PositiveOrZero
        @Digits(integer = 4, fraction = 2)
        BigDecimal proteins,

        @Schema(description = "Жиры в граммах", example = "8.0")
        @PositiveOrZero
        @Digits(integer = 4, fraction = 2)
        BigDecimal fats,

        @Schema(description = "Углеводы в граммах", example = "55.0")
        @PositiveOrZero
        @Digits(integer = 4, fraction = 2)
        BigDecimal carbohydrates,

        @Schema(description = "Ссылка на изображение", example = "https://image/6451...a84b7")
        @org.hibernate.validator.constraints.URL
        @Size(max = 512)
        String imageUrl
) {
}
