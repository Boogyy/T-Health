package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.FoodEntryEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodEntryRepository extends JpaRepository<FoodEntryEntity, UUID> {

    List<FoodEntryEntity> findAllByUser_KeycloakIdOrderByMealDateDesc(UUID userId);

    List<FoodEntryEntity> findAllByUser_KeycloakIdAndMealDateEqualsOrderByMealDateDesc(UUID userId, LocalDateTime date);

    Optional<FoodEntryEntity> findByIdAndUser_KeycloakId(UUID foodEntryId, UUID userId);

    long countByUser_KeycloakId(UUID userId);

    void deleteAllByUser_KeycloakId(UUID userId);
}