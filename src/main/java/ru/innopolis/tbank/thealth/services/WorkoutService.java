package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.WorkoutCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.UserResponse;
import ru.innopolis.tbank.thealth.dto.response.WorkoutResponse;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.WorkoutType;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkoutService {

    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;

    public WorkoutService(UserRepository userRepository,
                          WorkoutRepository workoutRepository) {
        this.userRepository = userRepository;
        this.workoutRepository = workoutRepository;
    }

    @Transactional
    public WorkoutResponse createWorkout(
            WorkoutCreateRequest workoutToCreate,
            UUID userId
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("No user with such id " + userId));


        WorkoutEntity workoutToSave = new WorkoutEntity();
        workoutToSave.setTitle(workoutToCreate.title());
        workoutToSave.setType(workoutToCreate.type());
        workoutToSave.setDescription(workoutToCreate.description());
        workoutToSave.setDurationMinutes(workoutToCreate.durationMinutes());
        workoutToSave.setCaloriesBurned(workoutToCreate.caloriesBurned());
        workoutToSave.setUser(user);

        WorkoutEntity savedEntity = workoutRepository.save(workoutToSave);

        return toResponse(savedEntity);
    }

    @Transactional
    public void deleteWorkout( UUID workoutId, UUID userId) {
        WorkoutEntity workout = workoutRepository.findByIdAndUser_KeycloakId(workoutId, userId).orElseThrow(
                () -> new IllegalArgumentException("Workout not found")
        );

        workoutRepository.delete(workout);
    }

    private WorkoutResponse toResponse(WorkoutEntity workoutEntity) {
        return new WorkoutResponse(
                workoutEntity.getId(),
                workoutEntity.getUser().getKeycloakId(),
                workoutEntity.getTitle(),
                workoutEntity.getType(),
                workoutEntity.getDescription(),
                workoutEntity.getDurationMinutes(),
                workoutEntity.getCaloriesBurned()
        );
    }

}
