package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.repositories.RecipeRepository;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecipeControllerIT extends AbstractIntegrationTest {

    @Autowired
    private RecipeRepository recipeRepository;

    @Test
    @DisplayName("POST /api/recipes сохраняет полноценный рецепт")
    void createRecipe_shouldReturn201AndPersist() throws Exception {
        persistUser(TestFixtures.USER_ID, "recipe-user");

        mockMvc.perform(post("/api/recipes")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Oatmeal",
                                  "description": "Healthy breakfast",
                                  "ingredients": "Oats, milk, berries",
                                  "cookingSteps": "Mix and cook",
                                  "calories": 350,
                                  "proteins": 12.50,
                                  "fats": 8.00,
                                  "carbohydrates": 55.00,
                                  "imageUrl": "https://example.com/oatmeal.jpg"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(TestFixtures.USER_ID.toString()))
                .andExpect(jsonPath("$.ingredients").value("Oats, milk, berries"))
                .andExpect(jsonPath("$.cookingSteps").value("Mix and cook"));

        assertThat(recipeRepository.findAllByUser_KeycloakIdOrderByCreatedAtDesc(TestFixtures.USER_ID))
                .singleElement()
                .satisfies(recipe -> {
                    assertThat(recipe.getCalories()).isEqualTo(350);
                    assertThat(recipe.getProteins()).isEqualByComparingTo("12.50");
                });
    }

    @Test
    @DisplayName("Отрицательное КБЖУ и некорректный URL возвращают 400")
    void createRecipe_withInvalidData_shouldReturn400() throws Exception {
        persistUser(TestFixtures.USER_ID, "recipe-user");

        mockMvc.perform(post("/api/recipes")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid recipe",
                                  "description": "Description",
                                  "ingredients": "Ingredients",
                                  "cookingSteps": "Steps",
                                  "calories": -1,
                                  "imageUrl": "not-a-url"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.calories").exists())
                .andExpect(jsonPath("$.validationErrors.imageUrl").exists());
    }

    @Test
    @DisplayName("Пользователь не может получить чужой рецепт")
    void getRecipe_ownedByAnotherUser_shouldReturn404() throws Exception {
        persistUser(TestFixtures.USER_ID, "requester");
        UserEntity owner = persistUser(TestFixtures.OTHER_USER_ID, "owner");

        RecipeEntity recipe = new RecipeEntity();
        recipe.setUser(owner);
        recipe.setTitle("Private recipe");
        recipe.setDescription("Description");
        recipe.setIngredients("Ingredients");
        recipe.setCookingSteps("Steps");
        recipe.setProteins(new BigDecimal("10.00"));
        recipe = recipeRepository.saveAndFlush(recipe);

        mockMvc.perform(get("/api/recipes/{id}", recipe.getId())
                        .with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recipe not found by id " + recipe.getId()));
    }
}
