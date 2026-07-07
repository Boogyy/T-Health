package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityRepository extends JpaRepository<CommunityEntity, UUID> {

    List<CommunityEntity> findAllByOrderByCreatedAtDesc();

    List<CommunityEntity> findAllByOwner_KeycloakIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<CommunityEntity> findByIdAndOwner_KeycloakId(UUID communityId, UUID ownerId);

    boolean existsByCommunityNameIgnoreCase(String communityName);
}