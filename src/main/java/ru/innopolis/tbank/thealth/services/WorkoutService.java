package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.WorkoutCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.WorkoutUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.UserResponse;
import ru.innopolis.tbank.thealth.dto.response.WorkoutResponse;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.WorkoutType;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.WorkoutNotFoundException;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkoutService {

    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;
    private final AchievementService achievementService;

    public WorkoutService(UserRepository userRepository,
                        WorkoutRepository workoutRepository,
                        AchievementService achievementService) {
        this.userRepository = userRepository;
        this.workoutRepository = workoutRepository;
        this.achievementService = achievementService;
    }

    @Transactional
    public WorkoutResponse createWorkout(
            WorkoutCreateRequest workoutToCreate,
            UUID userId
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException(userId));


        WorkoutEntity workoutToSave = new WorkoutEntity();
        workoutToSave.setTitle(workoutToCreate.title());
        workoutToSave.setType(workoutToCreate.type());
        workoutToSave.setDescription(workoutToCreate.description());
        workoutToSave.setDurationMinutes(workoutToCreate.durationMinutes());
        workoutToSave.setCaloriesBurned(workoutToCreate.caloriesBurned());
        workoutToSave.setUser(user);

        WorkoutEntity savedEntity = workoutRepository.save(workoutToSave);

        grantWorkoutAchievements(userId, savedEntity);

        return toResponse(savedEntity);
    }

    @Transactional
    public void deleteWorkout(UUID workoutId, UUID userId) {
        WorkoutEntity workout = findOwnedWorkout(workoutId, userId);
        workoutRepository.delete(workout);
    }

    @Transactional(readOnly = true)
    public List<WorkoutResponse> getAllWorkouts(UUID userId) {
        ensureUserExists(userId);

        return workoutRepository.findAllByUser_KeycloakIdOrderByWorkoutDateDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    @Transactional(readOnly = true)
    public WorkoutResponse getWorkout(UUID workoutId, UUID userId) {
        WorkoutEntity workout = findOwnedWorkout(workoutId, userId);
        return toResponse(workout);
    }

    @Transactional
    public WorkoutResponse updateWorkout(UUID workoutId, UUID userId, WorkoutUpdateRequest request) {
        WorkoutEntity workout = findOwnedWorkout(workoutId, userId);

        if (request.title() != null && !request.title().isBlank()) {
            workout.setTitle(request.title());
        }

        if (request.type() != null) {
            workout.setType(request.type());
        }

        if (request.description() != null) {
            workout.setDescription(request.description());
        }

        if (request.durationMinutes() != null) {
            workout.setDurationMinutes(request.durationMinutes());
        }

        if (request.caloriesBurned() != null) {
            workout.setCaloriesBurned(request.caloriesBurned());
        }

        return toResponse(workout);
    }

    private void grantWorkoutAchievements(UUID userId, WorkoutEntity workout) {
        long workoutsCount = workoutRepository.countByUser_KeycloakId(userId);

        if (workoutsCount >= 1) {
            achievementService.grantAchievementIfNotExists(userId, "FIRST_WORKOUT");
        }

        if (workoutsCount >= 5) {
            achievementService.grantAchievementIfNotExists(userId, "FIVE_WORKOUTS");
        }

        if (workout.getDurationMinutes() != null && workout.getDurationMinutes() >= 60) {
            achievementService.grantAchievementIfNotExists(userId, "LONG_WORKOUT");
        }

        if (workout.getCaloriesBurned() != null && workout.getCaloriesBurned() >= 500) {
            achievementService.grantAchievementIfNotExists(userId, "CALORIE_BURNER");
        }
    }

    private WorkoutEntity findOwnedWorkout(UUID workoutId, UUID userId) {
        return workoutRepository.findByIdAndUser_KeycloakId(workoutId, userId)
                .orElseThrow(() -> new WorkoutNotFoundException(workoutId));
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
    }

    private WorkoutResponse toResponse(WorkoutEntity workoutEntity) {
        return new WorkoutResponse(
                workoutEntity.getId(),
                workoutEntity.getUser().getKeycloakId(),
                workoutEntity.getTitle(),
                workoutEntity.getType(),
                workoutEntity.getDescription(),
                workoutEntity.getDurationMinutes(),
                workoutEntity.getCaloriesBurned(),
                workoutEntity.getWorkoutDate()
        );
    }
}
