package ru.innopolis.tbank.thealth.mappers;

import org.springframework.stereotype.Component;
import ru.innopolis.tbank.thealth.dto.response.AchievementResponse;
import ru.innopolis.tbank.thealth.dto.response.UserAchievementResponse;
import ru.innopolis.tbank.thealth.entities.AchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserAchievementEntity;

@Component
public class AchievementMapper {
    public AchievementResponse toAchievementResponse(AchievementEntity achievement) {
        if (achievement == null) {
            return null;
        }
        return new AchievementResponse(
                achievement.getId(),
                achievement.getCode(),
                achievement.getTitle(),
                achievement.getDescription(),
                achievement.getCreatedAt()
        );
    }

    public UserAchievementResponse toUserAchievementResponse(UserAchievementEntity userAchievement) {

        if (userAchievement == null) {
            return null;
        }

        return new UserAchievementResponse(
                userAchievement.getId(),
                userAchievement.getUser().getKeycloakId(),
                toAchievementResponse(userAchievement.getAchievement()),
                userAchievement.getReceivedAt()
        );
    }
}
