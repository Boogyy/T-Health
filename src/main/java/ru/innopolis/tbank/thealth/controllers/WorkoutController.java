package ru.innopolis.tbank.thealth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.WorkoutCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.WorkoutUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.dto.response.WorkoutResponse;
import ru.innopolis.tbank.thealth.services.WorkoutService;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Workouts",
        description = "Операции с тренировками текущего пользователя"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }


    @Operation(summary = "Получить все тренировки текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список тренировок получен"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<WorkoutResponse>> getAllUserWorkouts(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = workoutService.getAllWorkouts(userId);
        return ResponseEntity.ok(result);
    }


    @Operation(summary = "Получить тренировку по id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тренировка получена"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Тренировка не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<WorkoutResponse> getWorkout(
            @PathVariable("id") UUID workoutId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = workoutService.getWorkout(workoutId, userId);
        return ResponseEntity.ok(result);
    }



    @Operation(summary = "Создать тренировку")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Тренировка создана"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные запроса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping()
    public ResponseEntity<WorkoutResponse> createNewWorkout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutCreateRequest workoutToCreate
    ) {
        UUID userId = getUserId(jwt);
        var result = workoutService.createWorkout(workoutToCreate, userId);
        return ResponseEntity.status(201).body(result);
    }


    @Operation(summary = "Обновить тренировку")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тренировка обновлена"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные запроса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Тренировка не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/{id}")
    public ResponseEntity<WorkoutResponse> updateWorkout(
            @PathVariable("id") UUID workoutId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutUpdateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = workoutService.updateWorkout(workoutId, userId, request);
        return ResponseEntity.ok(result);
    }


    @Operation(summary = "Удалить тренировку")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Тренировка удалена",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Тренировка не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkout(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean deleteRelatedPost
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        workoutService.deleteWorkout(id, userId, deleteRelatedPost);
        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }


}
