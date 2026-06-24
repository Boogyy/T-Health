package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<WorkoutEntity, UUID> {

    Optional<WorkoutEntity> findByIdAndUser_KeycloakId(UUID workoutId, UUID userId);

    long countByUser_KeycloakId(UUID userId);
}
