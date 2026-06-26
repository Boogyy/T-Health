package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.UserAchievementEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAchievementRepository extends JpaRepository<UserAchievementEntity, UUID> {

    List<UserAchievementEntity> findAllByUser_KeycloakIdOrderByReceivedAtDesc(UUID userId);

    boolean existsByUser_KeycloakIdAndAchievement_Code(UUID userId, String achievementCode);

    Optional<UserAchievementEntity> findByIdAndUser_KeycloakId(UUID userAchievementId, UUID userId);

    long countByUser_KeycloakId(UUID userId);

    void deleteAllByUser_KeycloakId(UUID userId);
}