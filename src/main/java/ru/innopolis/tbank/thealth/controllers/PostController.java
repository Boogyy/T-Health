package ru.innopolis.tbank.thealth.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.WorkoutPostCreateRequest;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.services.PostService;

import java.util.UUID;

@RestController
@RequestMapping("api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/achievements/{id}/share")
    public ResponseEntity<String> postAchievementEntry(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt
            ) {
        var res = postService.postAchievement(id, jwt);
        return ResponseEntity.ok("Everything is fine");

    }

    @PostMapping("/food-entries/{id}/share")
    public ResponseEntity<String> postFoodEntry(
            @PathVariable("id") UUID id
    ) {
        var res = postService.postFood(id);
        return ResponseEntity.ok("Everything is fine");

    }

    @PostMapping("/workous/{id}/share")
    public ResponseEntity<String> postWorkoutEntry(
            @PathVariable("id") UUID id
    ) {
        var res = postService.postWorkout(id);
        return ResponseEntity.ok("Everything is fine");

    }

//    @PostMapping("/food-entry")
//    public ResponseEntity<String> createFoodPost(
//            @AuthenticationPrincipal Jwt jwt,
//            @RequestBody WorkoutPostCreateRequest workoutPostCreateRequest
//    ) {
//        var res = postService.createFoodEntryPost(jwt, workoutPostCreateRequest);
//        return ResponseEntity.ok("Everything is fine");
//
//    }

    @PostMapping("/workouts")
    public ResponseEntity<PostEntity> createWorkoutPost(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody WorkoutPostCreateRequest workoutPostCreateRequest
    ) {
        var res = postService.createWorkoutEntryPost(jwt, workoutPostCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);

    }




}
