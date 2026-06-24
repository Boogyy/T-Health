package ru.innopolis.tbank.thealth.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record AchievementResponse(
        UUID id,
        String code,
        String title,
        String description,
        LocalDateTime createdAt
) {
}