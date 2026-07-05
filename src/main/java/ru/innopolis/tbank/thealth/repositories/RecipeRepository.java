package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<RecipeEntity, UUID> {

    Optional<RecipeEntity> findByIdAndUser_KeycloakId(UUID recipeId, UUID userId);

    List<RecipeEntity> findAllByUser_KeycloakIdOrderByCreatedAtDesc(UUID userId);

    void deleteAllByUser_KeycloakId(UUID keycloakId);
}
