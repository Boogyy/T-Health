package ru.innopolis.tbank.thealth.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.RecipeCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.RecipeUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.RecipeResponse;
import ru.innopolis.tbank.thealth.services.RecipeService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getAllRecipes(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = recipeService.getAllUserRecipes(jwt);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getPreciseRecipe(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id

    ) {
        var result = recipeService.getRecipeById(id, jwt);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @PostMapping
    public ResponseEntity<RecipeResponse> createRecipe(
            @Valid @RequestBody RecipeCreateRequest recipeCreateRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var res = recipeService.createRecipe(recipeCreateRequest, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RecipeUpdateRequest recipeUpdateRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var res = recipeService.updateRecipe(id, jwt, recipeUpdateRequest);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean deleteRelatedPost
    ) {
        recipeService.deleteById(jwt, id, deleteRelatedPost);
        return ResponseEntity.noContent().build();
    }

}
