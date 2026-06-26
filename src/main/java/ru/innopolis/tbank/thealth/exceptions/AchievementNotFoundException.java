package ru.innopolis.tbank.thealth.exceptions;

public class AchievementNotFoundException extends NotFoundException {
    public AchievementNotFoundException(String achievementCode) {
        super("Achievement not found by code " + achievementCode);
    }
}
