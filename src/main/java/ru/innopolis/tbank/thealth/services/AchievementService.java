package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.response.AchievementResponse;
import ru.innopolis.tbank.thealth.dto.response.UserAchievementResponse;
import ru.innopolis.tbank.thealth.entities.AchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserAchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.repositories.AchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;

    public AchievementService(AchievementRepository achievementRepository,
                              UserAchievementRepository userAchievementRepository,
                              UserRepository userRepository) {
        this.achievementRepository = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AchievementResponse> getAllAchievements() {
        return achievementRepository.findAll()
                .stream()
                .map(this::toAchievementResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserAchievementResponse> getCurrentUserAchievements(UUID userId) {
        return userAchievementRepository.findAllByUser_KeycloakIdOrderByReceivedAtDesc(userId)
                .stream()
                .map(this::toUserAchievementResponse)
                .toList();
    }

    @Transactional
    public void grantAchievementIfNotExists(UUID userId, String achievementCode) {
        if (userAchievementRepository.existsByUser_KeycloakIdAndAchievement_Code(userId, achievementCode)) {
            return;
        }

        grantAchievement(userId, achievementCode);
        grantFiveAchievementsIfNeeded(userId);
    }

    private void grantFiveAchievementsIfNeeded(UUID userId) {
        String achievementCode = "FIVE_ACHIEVEMENTS";

        if (userAchievementRepository.existsByUser_KeycloakIdAndAchievement_Code(userId, achievementCode)) {
            return;
        }

        long achievementsCount = userAchievementRepository.countByUser_KeycloakId(userId);

        if (achievementsCount >= 5) {
            grantAchievement(userId, achievementCode);
        }
    }

    private void grantAchievement(UUID userId, String achievementCode) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AchievementEntity achievement = achievementRepository.findByCode(achievementCode)
                .orElseThrow(() -> new IllegalArgumentException("Achievement not found"));

        UserAchievementEntity userAchievement = new UserAchievementEntity();
        userAchievement.setUser(user);
        userAchievement.setAchievement(achievement);

        userAchievementRepository.save(userAchievement);
    }

    private AchievementResponse toAchievementResponse(AchievementEntity achievement) {
        return new AchievementResponse(
                achievement.getId(),
                achievement.getCode(),
                achievement.getTitle(),
                achievement.getDescription(),
                achievement.getCreatedAt()
        );
    }

    private UserAchievementResponse toUserAchievementResponse(UserAchievementEntity userAchievement) {
        return new UserAchievementResponse(
                userAchievement.getId(),
                userAchievement.getUser().getKeycloakId(),
                toAchievementResponse(userAchievement.getAchievement()),
                userAchievement.getReceivedAt()
        );
    }
}