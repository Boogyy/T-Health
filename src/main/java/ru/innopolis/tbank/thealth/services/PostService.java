package ru.innopolis.tbank.thealth.services;

import jakarta.validation.Valid;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.PostInfoRequest;
import ru.innopolis.tbank.thealth.dto.request.RecipePostCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.TextPostCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.WorkoutPostCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.entities.*;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.exceptions.*;
import ru.innopolis.tbank.thealth.mappers.PostMapper;
import ru.innopolis.tbank.thealth.repositories.*;

import java.util.List;
import java.util.UUID;


@Service
public class PostService {

    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;
    private final PostRepository postRepository;
    private final RecipeRepository recipeRepository;
    private final PostMapper postMapper;

    public PostService(
            UserAchievementRepository userAchievementRepository,
            UserRepository userRepository,
            WorkoutRepository workoutRepository,
            PostRepository postRepository,
            RecipeRepository recipeRepository,
            PostMapper postMapper
    ) {
        this.userAchievementRepository = userAchievementRepository;
        this.userRepository = userRepository;
        this.workoutRepository = workoutRepository;
        this.postRepository = postRepository;
        this.recipeRepository = recipeRepository;
        this.postMapper = postMapper;
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getPosts(PostType type) {
        if (type == null) {
            return postRepository.findAllByVisibilityOrderByCreatedAtDesc(PostVisibility.PUBLIC)
                    .stream()
                    .map(postMapper::toPostResponse)
                    .toList();
        }

        return postRepository.findAllByVisibilityAndPostTypeOrderByCreatedAtDesc(PostVisibility.PUBLIC, type)
                .stream()
                .map(postMapper::toPostResponse)
                .toList();

    }

    @Transactional(readOnly = true)
    public List<PostResponse> getUserPosts(Jwt jwt, PostType type) {
        UserEntity user = checkUser(jwt);
        if (type == null) {
            return postRepository.findAllByUser_KeycloakIdOrderByCreatedAtDesc(user.getKeycloakId())
                    .stream()
                    .map(postMapper::toPostResponse)
                    .toList();
        }

        return postRepository.findAllByUser_KeycloakIdAndPostTypeOrderByCreatedAtDesc(user.getKeycloakId(), type)
                .stream()
                .map(postMapper::toPostResponse)
                .toList();

    }

    @Transactional
    public PostResponse postAchievement(UUID id, Jwt jwt, PostInfoRequest request) {
        UserEntity user = checkUser(jwt);

        UserAchievementEntity userAchievement = userAchievementRepository
                .findByIdAndUser_KeycloakId(id, getKeycloakId(jwt))
                .orElseThrow(() -> new AchievementUserNotFoundException(id));

        if (postRepository.existsByUserAchievement_Id(id)) {
            throw new ConflictException("Achievement is already published");
        }

        PostEntity postToSave = new PostEntity();
        postToSave.setUser(user);
        postToSave.setUserAchievement(userAchievement);
        postToSave.setPostType(PostType.ACHIEVEMENT);
        postToSave.setTitle(request.postTitle());

        PostEntity savedPost = postRepository.save(postToSave);

        return postMapper.toPostResponse(savedPost) ;
    }

    @Transactional
    public PostResponse postRecipe(UUID id, Jwt jwt, PostInfoRequest request) {
        UserEntity user = checkUser(jwt);
        RecipeEntity recipe = recipeRepository
                .findByIdAndUser_KeycloakId(id, user.getKeycloakId())
                .orElseThrow(() -> new RecipeNotFoundException(id));

        if (postRepository.existsByRecipe_Id(id)) {
            throw new ConflictException("Recipe is already published");
        }

        PostEntity postToSave = createFromRecipe(user, recipe, request.postTitle());

        return postMapper.toPostResponse(postRepository.save(postToSave));
    }

    @Transactional
    public PostResponse postWorkout(UUID id, Jwt jwt, PostInfoRequest request) {
        UserEntity user = checkUser(jwt);

        WorkoutEntity workout = workoutRepository
                .findByIdAndUser_KeycloakId(id, getKeycloakId(jwt))
                .orElseThrow(() -> new WorkoutNotFoundException(id));

        if (postRepository.existsByWorkout_Id(id)) {
            throw new ConflictException("Workout is already published");
        }

        PostEntity postToSave = createFromWorkout(user, workout, request.postTitle());
        PostEntity savedPost = postRepository.save(postToSave);

        return postMapper.toPostResponse(savedPost);
    }

    @Transactional
    public PostResponse createRecipePost(
            Jwt jwt,
            RecipePostCreateRequest recipePostCreateRequest
    ) {
        UserEntity user = checkUser(jwt);
        RecipeEntity recipeToSave = new RecipeEntity();

        recipeToSave.setUser(user);
        recipeToSave.setTitle(recipePostCreateRequest.recipe().title());
        recipeToSave.setDescription(recipePostCreateRequest.recipe().description());
        recipeToSave.setIngredients(recipePostCreateRequest.recipe().ingredients());
        recipeToSave.setCookingSteps(recipePostCreateRequest.recipe().cookingSteps());
        recipeToSave.setCalories(recipePostCreateRequest.recipe().calories());
        recipeToSave.setProteins(recipePostCreateRequest.recipe().proteins());
        recipeToSave.setFats(recipePostCreateRequest.recipe().fats());
        recipeToSave.setCarbohydrates(recipePostCreateRequest.recipe().carbohydrates());
        recipeToSave.setImageUrl(recipePostCreateRequest.recipe().imageUrl());

        RecipeEntity savedRecipe = recipeRepository.save(recipeToSave);

        PostEntity postToSave = createFromRecipe(user, savedRecipe, recipePostCreateRequest.post().postTitle());

        return postMapper.toPostResponse(postRepository.save(postToSave));
    }


    @Transactional
    public PostResponse createWorkoutEntryPost(
            Jwt jwt,
            WorkoutPostCreateRequest workoutPostCreateRequest
    ) {
        UserEntity user = checkUser(jwt);

        WorkoutEntity workout = new WorkoutEntity();

        workout.setUser(user);
        workout.setTitle(workoutPostCreateRequest.workout().title());
        workout.setType(workoutPostCreateRequest.workout().type());
        workout.setDescription(workoutPostCreateRequest.workout().description());
        workout.setDurationMinutes(workoutPostCreateRequest.workout().durationMinutes());
        workout.setCaloriesBurned(workoutPostCreateRequest.workout().caloriesBurned());

        WorkoutEntity savedWorkout = workoutRepository.save(workout);

        PostEntity postEntity = createFromWorkout(user, savedWorkout, workoutPostCreateRequest.post().postTitle());
        PostEntity savedPost = postRepository.save(postEntity);

        return postMapper.toPostResponse(savedPost);
    }

    @Transactional
    public PostResponse createTextPost(
            Jwt jwt,
            TextPostCreateRequest textPostCreateRequest
    ) {
        UserEntity user = checkUser(jwt);

        PostEntity entityToSave = new PostEntity();
        entityToSave.setUser(user);
        entityToSave.setPostType(PostType.TEXT);
        entityToSave.setTitle(textPostCreateRequest.post().postTitle());
        entityToSave.setContent(textPostCreateRequest.content());

        return postMapper.toPostResponse(postRepository.save(entityToSave));
    }


    // ----- HELPERS -----


    private UUID getKeycloakId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private UserEntity checkUser(Jwt jwt) {
        return userRepository.findByKeycloakId(getKeycloakId(jwt))
                .orElseThrow(() -> new UserNotFoundException(getKeycloakId(jwt)));
    }




    private PostEntity createFromWorkout(
            UserEntity user,
            WorkoutEntity workout,
            String title
    ) {
        PostEntity postEntity = new PostEntity();
        postEntity.setUser(user);
        postEntity.setWorkout(workout);
        postEntity.setPostType(PostType.WORKOUT);
        postEntity.setTitle(title);
        return postEntity;
    }

    private PostEntity createFromRecipe(
            UserEntity user,
            RecipeEntity recipe,
            String title
    ) {
        PostEntity postEntity = new PostEntity();
        postEntity.setUser(user);
        postEntity.setRecipe(recipe);
        postEntity.setPostType(PostType.RECIPE);
        postEntity.setTitle(title);
        return postEntity;
    }
}
