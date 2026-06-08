package ru.innopolis.tbank.thealth.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.entities.WorkoutCreateRequest;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutResponse;
import ru.innopolis.tbank.thealth.services.WorkoutService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }


    @GetMapping()
    public ResponseEntity<List<WorkoutResponse>> getAllWorkouts() {
        List<WorkoutResponse> result = workoutService.getAllWorkouts();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/id")
    public ResponseEntity<Optional<WorkoutResponse>> getExactWorkout(@PathVariable("id")UUID id) {
        Optional<WorkoutResponse> result = workoutService.getWorkotuById(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add")
    public ResponseEntity<WorkoutResponse> createNewWorkout(@RequestBody WorkoutCreateRequest workoutToCreate) {
        var result = workoutService.createWorkout(workoutToCreate);
        return ResponseEntity.status(201).body(result);
    }

    @PutMapping("/change/id")
    private ResponseEntity<Optional<WorkoutResponse>> changeTrainingMethod(@PathVariable("id") Long id){
        Optional<WorkoutResponse> result = workoutService.changeById(id);
        return ResponseEntity.ok(result);

    }
}
