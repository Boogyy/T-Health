package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class AchievementUserNotFoundException extends NotFoundException {
    public AchievementUserNotFoundException(UUID achievementId) {
        super("User's achievement not found by id " + achievementId);
    }
}


