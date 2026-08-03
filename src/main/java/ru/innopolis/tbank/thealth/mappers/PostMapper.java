package ru.innopolis.tbank.thealth.mappers;

import org.springframework.stereotype.Component;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.entities.PostEntity;

@Component
public class PostMapper {

    private final WorkoutMapper workoutMapper;
    private final RecipeMapper recipeMapper;
    private final AchievementMapper achievementMapper;

    public PostMapper(
            WorkoutMapper workoutMapper,
            RecipeMapper recipeMapper,
            AchievementMapper achievementMapper
    ) {
        this.workoutMapper = workoutMapper;
        this.recipeMapper = recipeMapper;
        this.achievementMapper = achievementMapper;
    }

    public PostResponse toPostResponse (PostEntity savedPost) {
        return new PostResponse(
                savedPost.getId(),
                savedPost.getUser().getKeycloakId(),
                savedPost.getUser().getUsername(),
                savedPost.getCommunity() == null ? null : savedPost.getCommunity().getId(),
                savedPost.getTitle(),
                savedPost.getVisibility(),
                workoutMapper.toWorkoutResponse(savedPost.getWorkout()),
                recipeMapper.toRecipeResponse(savedPost.getRecipe()),
                achievementMapper.toUserAchievementResponse(savedPost.getUserAchievement()),
                savedPost.getPostType(),
                savedPost.getContent(),
                savedPost.getCommentsCount(),
                savedPost.getCreatedAt(),
                savedPost.getUpdatedAt()
        );
    }


}
