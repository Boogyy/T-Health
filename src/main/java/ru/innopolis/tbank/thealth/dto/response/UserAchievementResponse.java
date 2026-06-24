package ru.innopolis.tbank.thealth.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserAchievementResponse(
        UUID id,
        UUID userId,
        AchievementResponse achievement,
        LocalDateTime receivedAt
) {
}