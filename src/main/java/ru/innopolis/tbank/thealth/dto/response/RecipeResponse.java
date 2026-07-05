package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Информация о рецепте")
public record RecipeResponse(

        @Schema(description = "Идентификатор записи питания", example = "6b6c2f64-3a9b-4c37-b6d1-4e6b5e1a1111")
        UUID id,

        @Schema(description = "Идентификатор владельца записи из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID authorId,

        @Schema(description = "Название рецепта", example = "Овсянка с ягодами")
        String title,

        @Schema(description = "Калории", example = "350")
        Integer calories,

        @Schema(description = "Белки в граммах", example = "12.5")
        BigDecimal proteins,

        @Schema(description = "Жиры в граммах", example = "8.0")
        BigDecimal fats,

        @Schema(description = "Углеводы в граммах", example = "55.0")
        BigDecimal carbohydrates,

        @Schema(description = "Дата создания записи", example = "2026-06-26T09:10:00")
        LocalDateTime createdAt,

        @Schema(description = "Дата последнего обновления записи", example = "2026-06-26T09:10:00")
        LocalDateTime updatedAt
) {
}