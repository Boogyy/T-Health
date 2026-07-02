package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository extends JpaRepository<WorkoutEntity, UUID> {

    Optional<WorkoutEntity> findByIdAndUser_KeycloakId(UUID workoutId, UUID userId);

    List<WorkoutEntity> findAllByUser_KeycloakIdOrderByWorkoutDateDesc(UUID userId);

    long countByUser_KeycloakId(UUID userId);

    void deleteAllByUser_KeycloakId(UUID userId);
}
