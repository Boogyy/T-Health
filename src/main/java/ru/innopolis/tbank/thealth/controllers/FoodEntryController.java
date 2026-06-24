package ru.innopolis.tbank.thealth.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.FoodEntryResponse;
import ru.innopolis.tbank.thealth.services.FoodEntryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/food-entries")
public class FoodEntryController {

    private final FoodEntryService foodEntryService;

    public FoodEntryController(FoodEntryService foodEntryService) {
        this.foodEntryService = foodEntryService;
    }

    @GetMapping
    public ResponseEntity<List<FoodEntryResponse>> getCurrentUserFoodEntries(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = foodEntryService.getCurrentUserFoodEntries(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FoodEntryResponse> getFoodEntry(
            @PathVariable("id") UUID foodEntryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = foodEntryService.getFoodEntry(foodEntryId, userId);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<FoodEntryResponse> createFoodEntry(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FoodEntryCreateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = foodEntryService.createFoodEntry(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FoodEntryResponse> updateFoodEntry(
            @PathVariable("id") UUID foodEntryId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FoodEntryUpdateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = foodEntryService.updateFoodEntry(foodEntryId, userId, request);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFoodEntry(
            @PathVariable("id") UUID foodEntryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        foodEntryService.deleteFoodEntry(foodEntryId, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}