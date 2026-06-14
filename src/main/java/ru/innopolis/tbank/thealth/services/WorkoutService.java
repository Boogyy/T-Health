package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import ru.innopolis.tbank.thealth.dto.request.WorkoutCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.WorkoutResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkoutService {

    public List<WorkoutResponse> getAllWorkouts() {
        return null;
    }

    public Optional<WorkoutResponse> getWorkotuById(UUID id) {
        return null;
    }

    public WorkoutResponse createWorkout(WorkoutCreateRequest workoutToCreate) {
        return null;
    }

    public Optional<WorkoutResponse> changeById(Long id) {
        return null;
    }


}
