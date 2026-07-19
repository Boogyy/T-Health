package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.CommunityMemberEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityMemberRepository extends JpaRepository<CommunityMemberEntity, UUID> {

    List<CommunityMemberEntity> findAllByCommunity_IdOrderByJoinedAtDesc(UUID communityId);

    List<CommunityMemberEntity> findAllByUser_KeycloakIdOrderByJoinedAtDesc(UUID userId);

    Optional<CommunityMemberEntity> findByCommunity_IdAndUser_KeycloakId(UUID communityId, UUID userId);

    boolean existsByCommunity_IdAndUser_KeycloakId(UUID communityId, UUID userId);

    long countByCommunity_Id(UUID communityId);

    void deleteByCommunity_IdAndUser_KeycloakId(UUID communityId, UUID userId);

    void deleteAllByCommunity_Id(UUID communityId);

    void deleteAllByUser_KeycloakId(UUID userId);
}