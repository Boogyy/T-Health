package ru.innopolis.tbank.thealth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.response.AchievementResponse;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.dto.response.UserAchievementResponse;
import ru.innopolis.tbank.thealth.services.AchievementService;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Achievements",
        description = "Операции с достижениями пользователя"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @Operation(summary = "Получить список всех доступных достижений")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список достижений получен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/achievements")
    public ResponseEntity<List<AchievementResponse>> getAllAchievements() {
        var result = achievementService.getAllAchievements();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Получить достижения текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Достижения пользователя получены"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/users/me/achievements")
    public ResponseEntity<List<UserAchievementResponse>> getCurrentUserAchievements(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = achievementService.getCurrentUserAchievements(userId);
        return ResponseEntity.ok(result);
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}