package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.dto.response.PublicCommunityResponse;
import ru.innopolis.tbank.thealth.dto.response.PublicUserProfileResponse;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.PostMapper;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;

@Service
public class PublicUserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final PostMapper postMapper;

    public PublicUserService(
            UserRepository userRepository,
            PostRepository postRepository,
            CommunityMemberRepository communityMemberRepository,
            PostMapper postMapper
    ) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.communityMemberRepository =
                communityMemberRepository;
        this.postMapper = postMapper;
    }

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfile(
            String username
    ) {
        // точный поиск с учётом регистра
        UserEntity user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new UserNotFoundException(username)
                );

        // только PUBLIC-публикации.
        List<PostResponse> publications =
                postRepository
                        .findAllByUser_KeycloakIdAndVisibilityOrderByCreatedAtDesc(
                                user.getKeycloakId(),
                                PostVisibility.PUBLIC
                        )
                        .stream()
                        .map(postMapper::toPostResponse)
                        .toList();


        List<PublicCommunityResponse> communities =
                communityMemberRepository
                        .findAllByUser_KeycloakIdOrderByJoinedAtDesc(
                                user.getKeycloakId()
                        )
                        .stream()
                        .map(member ->
                                new PublicCommunityResponse(
                                        member.getCommunity().getId(),
                                        member.getCommunity().getCommunityName(),
                                        member.getCommunity().getDescription(),
                                        member.getRole(),
                                        member.getJoinedAt()
                                )
                        )
                        .toList();

        return new PublicUserProfileResponse(
                user.getKeycloakId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt(),
                publications,
                communities
        );
    }
}