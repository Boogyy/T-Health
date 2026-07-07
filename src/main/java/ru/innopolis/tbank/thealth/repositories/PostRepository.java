package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    @EntityGraph(attributePaths = {
            "user",
            "workout",
            "recipe",
            "userAchievement",
            "userAchievement.achievement",
            "community"
    })
    List<PostEntity> findAllByVisibilityOrderByCreatedAtDesc(PostVisibility visibility);

    List<PostEntity> findAllByVisibilityAndPostTypeOrderByCreatedAtDesc(PostVisibility visibility, PostType type);

    List<PostEntity> findAllByUser_KeycloakIdOrderByCreatedAtDesc(UUID userId);

    List<PostEntity> findAllByUser_KeycloakIdAndPostTypeOrderByCreatedAtDesc(UUID userId, PostType type);

<<<<<<< HEAD
    Optional<PostEntity> findByIdAndVisibility(UUID id, PostVisibility visibility);

    Optional<PostEntity> findByIdAndUser_KeycloakId(UUID postId, UUID userId);

    Optional<PostEntity> findByWorkout_Id(UUID workoutId);

    Optional<PostEntity> findByRecipe_Id(UUID recipeId);
=======
    List<PostEntity> findAllByCommunity_IdOrderByCreatedAtDesc(UUID communityId);
>>>>>>> 122d970 (feat: add communities and comments)

    boolean existsByWorkout_Id(UUID workoutId);

    boolean existsByRecipe_Id(UUID recipeId);

    boolean existsByUserAchievement_Id(UUID userAchievementId);

    void deleteAllByUser_KeycloakId(UUID keycloakId);

    void deleteAllByCommunity_Id(UUID communityId);
}