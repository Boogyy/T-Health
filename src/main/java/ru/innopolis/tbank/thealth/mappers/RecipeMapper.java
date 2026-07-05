package ru.innopolis.tbank.thealth.mappers;

import org.springframework.stereotype.Component;
import ru.innopolis.tbank.thealth.dto.response.RecipeResponse;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;

@Component
public class RecipeMapper {
    public RecipeResponse toRecipeResponse(RecipeEntity recipe) {
        if (recipe == null) {
            return null;
        }

        return new RecipeResponse(
                recipe.getId(),
                recipe.getUser().getKeycloakId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getIngredients(),
                recipe.getCalories(),
                recipe.getProteins(),
                recipe.getFats(),
                recipe.getCarbohydrates(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt()
        );
    }
}
