package ru.innopolis.tbank.thealth.services;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.RecipeCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.RecipeUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.RecipeResponse;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.ConflictException;
import ru.innopolis.tbank.thealth.exceptions.RecipeNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.RecipeMapper;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.RecipeRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecipeService {

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeMapper recipeMapper;
    private final PostRepository postRepository;
    private final PostDeletionService postDeletionService;

    public RecipeService(UserRepository userRepository,
                         RecipeRepository recipeRepository,
                         RecipeMapper recipeMapper,
                         PostRepository postRepository,
                         PostDeletionService postDeletionService) {
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
        this.recipeMapper = recipeMapper;
        this.postRepository = postRepository;
        this.postDeletionService = postDeletionService;
    }


    @Transactional
    public RecipeResponse createRecipe(
            RecipeCreateRequest request,
            Jwt jwt
    ) {
        RecipeEntity savedRecipe = createRecipeEntity(request, getId(jwt));
        return recipeMapper.toRecipeResponse(savedRecipe);
    }

    @Transactional
    public RecipeEntity createRecipeEntity(
            RecipeCreateRequest request,
            UUID userId
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        RecipeEntity recipeToSave = new RecipeEntity();
        recipeToSave.setUser(user);
        recipeToSave.setTitle(request.title());
        recipeToSave.setDescription(request.description());
        recipeToSave.setIngredients(request.ingredients());
        recipeToSave.setCookingSteps(request.cookingSteps());
        recipeToSave.setCalories(request.calories());
        recipeToSave.setProteins(request.proteins());
        recipeToSave.setFats(request.fats());
        recipeToSave.setCarbohydrates(request.carbohydrates());
        recipeToSave.setImageUrl(request.imageUrl());

        return recipeRepository.save(recipeToSave);
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

        if (recipeUpdateRequest.title() != null) {
            recipe.setTitle(recipeUpdateRequest.title());
        }

        if (recipeUpdateRequest.description() != null) {
            recipe.setDescription(recipeUpdateRequest.description());
        }

        if (recipeUpdateRequest.ingredients() != null) {
            recipe.setIngredients(recipeUpdateRequest.ingredients());
        }

        if (recipeUpdateRequest.cookingSteps() != null) {
            recipe.setCookingSteps(recipeUpdateRequest.cookingSteps());
        }

        if (recipeUpdateRequest.imageUrl() != null) {
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


    @Transactional(readOnly = true)
    public List<RecipeResponse> getAllUserRecipes(Jwt jwt) {
        UserEntity user = ensureExist(jwt);
        return recipeRepository.findAllByUser_KeycloakIdOrderByCreatedAtDesc(user.getKeycloakId())
                .stream()
                .map(recipeMapper::toRecipeResponse)
                .toList();

    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipeById(UUID recipeId, Jwt jwt) {
        RecipeEntity recipe = findOwnedRecipe(recipeId, getId(jwt));
        return recipeMapper.toRecipeResponse(recipe);
    }

    @Transactional
    public void deleteById(Jwt jwt, UUID id, boolean deleteRelatedPost) {
        UUID userId = getId(jwt);
        RecipeEntity recipeToDelete = findOwnedRecipe(id, userId);

        Optional<PostEntity> relatedPost = postRepository.findByRecipe_Id(id);

        if (relatedPost.isPresent() && !deleteRelatedPost) {
            throw new ConflictException(
                    "Recipe is published. Confirm deletion to remove related post."
            );
        }

        relatedPost.ifPresent(post ->
                postDeletionService.deleteOwnedPost(post.getId(), userId)
        );

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
