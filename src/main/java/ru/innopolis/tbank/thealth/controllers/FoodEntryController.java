package ru.innopolis.tbank.thealth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.DailyFoodEntriesResponse;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.dto.response.FoodEntryResponse;
import ru.innopolis.tbank.thealth.services.FoodEntryService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(
        name = "Food Entries",
        description = "Операции с дневником питания текущего пользователя"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/food-entries")
public class FoodEntryController {

    private final FoodEntryService foodEntryService;

    public FoodEntryController(FoodEntryService foodEntryService) {
        this.foodEntryService = foodEntryService;
    }

    @Operation(summary = "Получить все записи питания текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список записей питания получен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<FoodEntryResponse>> getCurrentUserFoodEntries(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = foodEntryService.getCurrentUserFoodEntries(userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Получить запись питания по id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Запись питания получена"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запись питания не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<FoodEntryResponse> getFoodEntry(
            @PathVariable("id") UUID foodEntryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = foodEntryService.getFoodEntry(foodEntryId, userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Создать запись питания")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Запись питания создана"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<FoodEntryResponse> createFoodEntry(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FoodEntryCreateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = foodEntryService.createFoodEntry(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Обновить запись питания")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Запись питания обновлена"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные запроса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запись питания не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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

    @Operation(summary = "Удалить запись питания")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Запись питания удалена", content = @Content),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Запись питания не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFoodEntry(
            @PathVariable("id") UUID foodEntryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        foodEntryService.deleteFoodEntry(foodEntryId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/daily")
    public ResponseEntity<DailyFoodEntriesResponse> getDailyFoodEntries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        DailyFoodEntriesResponse response = foodEntryService.getDailyFoodEntries(jwt, date);
        return ResponseEntity.ok(response);

    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}