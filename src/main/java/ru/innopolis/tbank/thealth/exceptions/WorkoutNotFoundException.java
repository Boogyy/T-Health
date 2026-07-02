package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class WorkoutNotFoundException extends NotFoundException {

    public WorkoutNotFoundException(UUID workoutId) {
        super("Workout not found by id " + workoutId);
    }
}