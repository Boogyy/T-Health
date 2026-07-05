package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.PostVisibility;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    List<PostEntity> findAllByVisibilityOrderByCreatedAtDesc(PostVisibility visibility);

    List<PostEntity> findAllByUser_KeycloakIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByWorkout_Id(UUID workoutId);

    boolean existsByFoodEntry_Id(UUID foodEntryId);

    boolean existsByUserAchievement_Id(UUID userAchievementId);

}
