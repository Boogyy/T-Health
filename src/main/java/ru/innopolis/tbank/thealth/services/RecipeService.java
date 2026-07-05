package ru.innopolis.tbank.thealth.services;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.RecipeCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.RecipeUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.RecipeResponse;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.RecipeNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.RecipeMapper;
import ru.innopolis.tbank.thealth.repositories.RecipeRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class RecipeService {

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeMapper recipeMapper;

    public RecipeService(UserRepository userRepository,
                         RecipeRepository recipeRepository,
                         RecipeMapper recipeMapper) {
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
        this.recipeMapper = recipeMapper;
    }


    @Transactional
    public RecipeResponse createRecipe (
            RecipeCreateRequest recipeCreateRequest,
            Jwt jwt
    ) {
        UserEntity user = ensureExist(jwt);
        RecipeEntity recipeToSave = new RecipeEntity();
        recipeToSave.setUser(user);
        recipeToSave.setTitle(recipeCreateRequest.title());
        recipeToSave.setDescription(recipeCreateRequest.description());
        recipeToSave.setIngredients(recipeCreateRequest.ingredients());
        recipeToSave.setCookingSteps(recipeCreateRequest.cookingSteps());
        recipeToSave.setCalories(recipeCreateRequest.calories());
        recipeToSave.setProteins(recipeCreateRequest.proteins());
        recipeToSave.setFats(recipeCreateRequest.fats());
        recipeToSave.setCarbohydrates(recipeCreateRequest.carbohydrates());
        recipeToSave.setImageUrl(recipeCreateRequest.imageUrl());

        var recipe = recipeRepository.save(recipeToSave);
        return recipeMapper.toRecipeResponse(recipe);
    }

    @Transactional
    public RecipeResponse updateRecipe(
            UUID recipeId,
            Jwt jwt,
            RecipeUpdateRequest recipeUpdateRequest

    ) {
        UUID userId = getId(jwt);
        RecipeEntity recipe = recipeRepository.findByIdAndUser_KeycloakId(recipeId, userId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));

        if (recipeUpdateRequest.title() != null && !recipeUpdateRequest.title().isBlank()) {
            recipe.setTitle(recipeUpdateRequest.title());
        }

        if (recipeUpdateRequest.description() != null && !recipeUpdateRequest.description().isBlank()) {
            recipe.setDescription(recipeUpdateRequest.description());
        }

        if (recipeUpdateRequest.ingredients() != null && !recipeUpdateRequest.ingredients().isBlank()) {
            recipe.setIngredients(recipeUpdateRequest.ingredients());
        }

        if (recipeUpdateRequest.cookingSteps() != null && !recipeUpdateRequest.cookingSteps().isBlank()) {
            recipe.setCookingSteps(recipeUpdateRequest.cookingSteps());
        }

        if (recipeUpdateRequest.imageUrl() != null && !recipeUpdateRequest.imageUrl().isBlank()) {
            recipe.setImageUrl(recipeUpdateRequest.imageUrl());
        }

        if (recipeUpdateRequest.calories() != null) {
            recipe.setCalories(recipeUpdateRequest.calories());
        }

        if (recipeUpdateRequest.proteins() != null) {
            recipe.setProteins(recipeUpdateRequest.proteins());
        }

        if (recipeUpdateRequest.fats() != null) {
            recipe.setFats(recipeUpdateRequest.fats());
        }

        if (recipeUpdateRequest.carbohydrates() != null) {
            recipe.setCarbohydrates(recipeUpdateRequest.carbohydrates());
        }



        return recipeMapper.toRecipeResponse(recipe);

    }


    @Transactional
    public List<RecipeResponse> getAllUserRecipes(Jwt jwt) {
        UserEntity user = ensureExist(jwt);
        return recipeRepository.findAllByUser_KeycloakIdOrderByCreatedAtDesc(user.getKeycloakId())
                .stream()
                .map(recipeMapper::toRecipeResponse)
                .toList();

    }

    @Transactional
    public RecipeResponse getRecipeById(UUID recipeId, Jwt jwt) {
        RecipeEntity recipe = findOwnedRecipe(recipeId, getId(jwt));
        return recipeMapper.toRecipeResponse(recipe);
    }

    @Transactional
    public void deleteById(Jwt jwt, UUID id) {
        RecipeEntity recipeToDelete = findOwnedRecipe(id, getId(jwt));
        recipeRepository.delete(recipeToDelete);
    }



    // ----- HELPERS ------

    private UUID getId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }


    private RecipeEntity findOwnedRecipe(UUID recipeId, UUID userId) {
        return recipeRepository.findByIdAndUser_KeycloakId(recipeId, userId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));
    }

    private UserEntity ensureExist(Jwt jwt) {
        return userRepository.findByKeycloakId(getId(jwt))
                .orElseThrow(() -> new UserNotFoundException(getId(jwt)));
    }



}
