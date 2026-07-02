package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.AchievementEntity;

import java.util.Optional;
import java.util.UUID;

public interface AchievementRepository extends JpaRepository<AchievementEntity, UUID> {

    Optional<AchievementEntity> findByCode(String code);
}