package ru.innopolis.tbank.thealth.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.response.AchievementResponse;
import ru.innopolis.tbank.thealth.dto.response.UserAchievementResponse;
import ru.innopolis.tbank.thealth.services.AchievementService;

import java.util.List;
import java.util.UUID;

@RestController
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @GetMapping("/api/achievements")
    public ResponseEntity<List<AchievementResponse>> getAllAchievements() {
        var result = achievementService.getAllAchievements();
        return ResponseEntity.ok(result);
    }

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