package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    List<PostEntity> findAllByVisibilityOrderByCreatedAtDesc(PostVisibility visibility);

    List<PostEntity> findAllByVisibilityAndPostTypeOrderByCreatedAtDesc(PostVisibility visibility, PostType type);

    List<PostEntity> findAllByUser_KeycloakIdOrderByCreatedAtDesc(UUID userId);

    List<PostEntity> findAllByUser_KeycloakIdAndPostTypeOrderByCreatedAtDesc(UUID userId, PostType type);

    List<PostEntity> findAllByCommunity_IdOrderByCreatedAtDesc(UUID communityId);

    boolean existsByWorkout_Id(UUID workoutId);

    boolean existsByRecipe_Id(UUID recipeId);

    boolean existsByUserAchievement_Id(UUID userAchievementId);

    void deleteAllByUser_KeycloakId(UUID keycloakId);

    void deleteAllByCommunity_Id(UUID communityId);
}