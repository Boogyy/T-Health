package ru.innopolis.tbank.thealth.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.*;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.services.PostService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getAllPosts (
            @RequestParam(required = false) PostType type
    ) {
        var res = postService.getPosts(type);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @GetMapping("/me")
    public ResponseEntity<List<PostResponse>> getUserPosts (
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) PostType type
            ) {
        var res = postService.getUserPosts(jwt, type);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @PostMapping("/achievements/{id}/share")
    public ResponseEntity<PostResponse> postAchievement(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PostInfoRequest request
            ) {
        var res = postService.postAchievement(id, jwt, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);

    }

    @PostMapping("/recipes/{id}/share")
    public ResponseEntity<PostResponse> postRecipe(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id,
            @Valid @RequestBody PostInfoRequest request
    ) {
        var res = postService.postRecipe(id, jwt, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);

    }

    @PostMapping("/workouts/{id}/share")
    public ResponseEntity<PostResponse> postWorkout(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id,
            @Valid @RequestBody PostInfoRequest request
    ) {
        var res = postService.postWorkout(id, jwt, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);

    }

    @PostMapping("/workouts")
    public ResponseEntity<PostResponse> createWorkoutPost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutPostCreateRequest workoutPostCreateRequest
    ) {
        var res = postService.createWorkoutEntryPost(jwt, workoutPostCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);

    }

    @PostMapping("/recipes")
    public ResponseEntity<PostResponse> createRecipePost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecipePostCreateRequest recipePostCreateRequest
    ) {
        var res = postService.createRecipePost(jwt, recipePostCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);

    }

    @PostMapping("/text")
    public ResponseEntity<PostResponse> createTextPost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TextPostCreateRequest textPostCreateRequest
    ) {
        var res = postService.createTextPost(jwt, textPostCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);

    }
}
