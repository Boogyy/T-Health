package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecipeUpdateRequest (
        @Schema(description = "Название рецепта", example = "Овсянка с ягодами")
        @Size(max = 128)
        String title,

        @Schema(description = "Описание", example = "Высокоуглеводный завтрак")
        @Size(max = 512)
        String description,

        @Schema(description = "Ингредиенты для блюда", example = "Овсяные хлопья, клубника")
        @Size(max = 2000)
        String ingredients,

        @Schema(description = "Шаги приготовления", example = "Залить овсянку в соотношении 1 к 3...")
        @Size(max = 4000)
        String cookingSteps,

        @Schema(description = "Калории", example = "350")
        @PositiveOrZero
        Integer calories,

        @Schema(description = "Белки в граммах", example = "12.5")
        @PositiveOrZero
        BigDecimal proteins,

        @Schema(description = "Жиры в граммах", example = "8.0")
        @PositiveOrZero
        BigDecimal fats,

        @Schema(description = "Углеводы в граммах", example = "55.0")
        @PositiveOrZero
        BigDecimal carbohydrates,

        @Schema(description = "Ссылка на картинку блюда", example = "https://image/6451...a84b7")
        @org.hibernate.validator.constraints.URL
        @Size(max = 512)
        String imageUrl
) {
}
