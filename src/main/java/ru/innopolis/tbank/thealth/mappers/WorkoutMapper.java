package ru.innopolis.tbank.thealth.mappers;

import org.springframework.stereotype.Component;
import ru.innopolis.tbank.thealth.dto.response.WorkoutResponse;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;

@Component
public class WorkoutMapper {
    public WorkoutResponse toWorkoutResponse(WorkoutEntity workoutEntity) {
        if (workoutEntity == null) {
            return null;
        }

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
