package ru.innopolis.tbank.thealth.services;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.CommunityCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.CommunityPostCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.CommunityUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.CommunityMemberResponse;
import ru.innopolis.tbank.thealth.dto.response.CommunityResponse;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.entities.CommunityMemberEntity;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.enums.CommunityRole;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.exceptions.BadRequestException;
import ru.innopolis.tbank.thealth.exceptions.CommunityAlreadyExistsException;
import ru.innopolis.tbank.thealth.exceptions.CommunityMemberAlreadyExistsException;
import ru.innopolis.tbank.thealth.exceptions.CommunityMemberNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.CommunityNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.CommunityMapper;
import ru.innopolis.tbank.thealth.mappers.PostMapper;
import ru.innopolis.tbank.thealth.repositories.CommentRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommunityMapper communityMapper;
    private final PostMapper postMapper;

    public CommunityService(CommunityRepository communityRepository,
                            CommunityMemberRepository communityMemberRepository,
                            UserRepository userRepository,
                            PostRepository postRepository,
                            CommentRepository commentRepository,
                            CommunityMapper communityMapper,
                            PostMapper postMapper) {
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.communityMapper = communityMapper;
        this.postMapper = postMapper;
    }

    @Transactional(readOnly = true)
    public List<CommunityResponse> getAllCommunities(UUID currentUserId) {
        return communityRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(community -> toCommunityResponse(community, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityResponse> getCurrentUserCommunities(UUID currentUserId) {
        return communityMemberRepository.findAllByUser_KeycloakIdOrderByJoinedAtDesc(currentUserId)
                .stream()
                .map(CommunityMemberEntity::getCommunity)
                .map(community -> toCommunityResponse(community, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityResponse getCommunity(UUID communityId, UUID currentUserId) {
        CommunityEntity community = findCommunity(communityId);
        return toCommunityResponse(community, currentUserId);
    }

    @Transactional
    public CommunityResponse createCommunity(CommunityCreateRequest request, UUID currentUserId) {
        UserEntity owner = findUser(currentUserId);

        if (communityRepository.existsByCommunityNameIgnoreCase(request.communityName())) {
            throw new CommunityAlreadyExistsException(request.communityName());
        }

        CommunityEntity community = new CommunityEntity();
        community.setOwner(owner);
        community.setCommunityName(request.communityName());
        community.setDescription(request.description());

        CommunityEntity savedCommunity = communityRepository.save(community);

        CommunityMemberEntity ownerMember = new CommunityMemberEntity();
        ownerMember.setCommunity(savedCommunity);
        ownerMember.setUser(owner);
        ownerMember.setRole(CommunityRole.OWNER);

        communityMemberRepository.save(ownerMember);

        return toCommunityResponse(savedCommunity, currentUserId);
    }

    @Transactional
    public CommunityResponse updateCommunity(
            UUID communityId,
            UUID currentUserId,
            CommunityUpdateRequest request
    ) {
        CommunityEntity community = findCommunity(communityId);
        checkOwner(community, currentUserId);

        if (request.communityName() != null) {
            if (request.communityName().isBlank()) {
                throw new BadRequestException("Community name must not be blank");
            }

            boolean nameChanged = !community.getCommunityName().equalsIgnoreCase(request.communityName());

            if (nameChanged && communityRepository.existsByCommunityNameIgnoreCase(request.communityName())) {
                throw new CommunityAlreadyExistsException(request.communityName());
            }

            community.setCommunityName(request.communityName());
        }

        if (request.description() != null) {
            community.setDescription(request.description());
        }

        return toCommunityResponse(community, currentUserId);
    }

    @Transactional
    public void deleteCommunity(UUID communityId, UUID currentUserId) {
        CommunityEntity community = findCommunity(communityId);
        checkOwner(community, currentUserId);

        List<PostEntity> communityPosts = postRepository.findAllByCommunity_IdOrderByCreatedAtDesc(communityId);

        for (PostEntity post : communityPosts) {
            commentRepository.deleteAllByPost_Id(post.getId());
        }

        postRepository.deleteAllByCommunity_Id(communityId);
        communityMemberRepository.deleteAllByCommunity_Id(communityId);
        communityRepository.delete(community);
    }

    @Transactional
    public CommunityResponse joinCommunity(UUID communityId, UUID currentUserId) {
        CommunityEntity community = findCommunity(communityId);
        UserEntity user = findUser(currentUserId);

        if (communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(communityId, currentUserId)) {
            throw new CommunityMemberAlreadyExistsException(communityId);
        }

        CommunityMemberEntity communityMember = new CommunityMemberEntity();
        communityMember.setCommunity(community);
        communityMember.setUser(user);
        communityMember.setRole(CommunityRole.MEMBER);

        communityMemberRepository.save(communityMember);

        return toCommunityResponse(community, currentUserId);
    }

    @Transactional
    public void leaveCommunity(UUID communityId, UUID currentUserId) {
        CommunityEntity community = findCommunity(communityId);

        if (community.getOwner().getKeycloakId().equals(currentUserId)) {
            throw new BadRequestException("Community owner cannot leave own community");
        }

        if (!communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(communityId, currentUserId)) {
            throw new CommunityMemberNotFoundException(communityId);
        }

        communityMemberRepository.deleteByCommunity_IdAndUser_KeycloakId(communityId, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<CommunityMemberResponse> getCommunityMembers(UUID communityId) {
        findCommunity(communityId);

        return communityMemberRepository.findAllByCommunity_IdOrderByJoinedAtDesc(communityId)
                .stream()
                .map(communityMapper::toCommunityMemberResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getCommunityPosts(UUID communityId) {
        findCommunity(communityId);

        return postRepository.findAllByCommunity_IdOrderByCreatedAtDesc(communityId)
                .stream()
                .map(postMapper::toPostResponse)
                .toList();
    }

    @Transactional
    public PostResponse createCommunityTextPost(
            UUID communityId,
            UUID currentUserId,
            CommunityPostCreateRequest request
    ) {
        CommunityEntity community = findCommunity(communityId);
        UserEntity user = findUser(currentUserId);

        if (!communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(communityId, currentUserId)) {
            throw new CommunityMemberNotFoundException(communityId);
        }

        PostEntity post = new PostEntity();
        post.setUser(user);
        post.setCommunity(community);
        post.setVisibility(PostVisibility.COMMUNITY);
        post.setPostType(PostType.TEXT);
        post.setTitle(request.title());
        post.setContent(request.content());

        PostEntity savedPost = postRepository.save(post);

        return postMapper.toPostResponse(savedPost);
    }

    private CommunityEntity findCommunity(UUID communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findByKeycloakId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void checkOwner(CommunityEntity community, UUID currentUserId) {
        if (!community.getOwner().getKeycloakId().equals(currentUserId)) {
            throw new AccessDeniedException("Only community owner can perform this action");
        }
    }

    private CommunityResponse toCommunityResponse(CommunityEntity community, UUID currentUserId) {
        long membersCount = communityMemberRepository.countByCommunity_Id(community.getId());

        boolean currentUserMember = communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(
                community.getId(),
                currentUserId
        );

        return communityMapper.toCommunityResponse(
                community,
                membersCount,
                currentUserMember
        );
    }
}