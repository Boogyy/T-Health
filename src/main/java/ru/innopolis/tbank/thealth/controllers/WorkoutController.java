package ru.innopolis.tbank.thealth.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.WorkoutCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.WorkoutResponse;
import ru.innopolis.tbank.thealth.services.WorkoutService;

import java.util.UUID;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }


    @PostMapping()
    public ResponseEntity<WorkoutResponse> createNewWorkout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutCreateRequest workoutToCreate
    ) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        var result = workoutService.createWorkout(workoutToCreate, userId);
        return ResponseEntity.status(201).body(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkout(
            @PathVariable("id") UUID workoutId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
        workoutService.deleteWorkout(workoutId, userId);
        return ResponseEntity.noContent().build();
    }


}
