package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CommunityDeletionService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final PostDeletionService postDeletionService;

    public CommunityDeletionService(
            CommunityRepository communityRepository,
            CommunityMemberRepository communityMemberRepository,
            PostDeletionService postDeletionService
    ) {
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.postDeletionService = postDeletionService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllOwnedCommunities(UUID ownerId) {
        List<CommunityEntity> ownedCommunities =
                communityRepository.findAllByOwner_KeycloakId(ownerId);

        ownedCommunities.forEach(this::deleteCommunityWithRelations);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeUserFromOtherCommunities(UUID userId) {
        communityMemberRepository.deleteAllByUser_KeycloakId(userId);
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteCommunityWithRelations(
            CommunityEntity community
    ) {
        UUID communityId = community.getId();

        // Удаляет посты всех участников сообщества
        // и комментарии под этими постами.
        postDeletionService.deleteAllPostsByCommunity(communityId);

        // Удаляет OWNER, MEMBER и других участников.
        communityMemberRepository
                .deleteAllByCommunity_Id(communityId);

        communityRepository.delete(community);
    }
}