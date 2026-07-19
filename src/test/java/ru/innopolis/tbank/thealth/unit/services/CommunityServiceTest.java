package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.innopolis.tbank.thealth.dto.request.CommunityCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.CommunityPostCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.CommunityUpdateRequest;
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
import ru.innopolis.tbank.thealth.mappers.CommunityMapper;
import ru.innopolis.tbank.thealth.mappers.PostMapper;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.CommunityDeletionService;
import ru.innopolis.tbank.thealth.services.CommunityService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111");
    private static final UUID MEMBER_ID = UUID.fromString("22222222-bbbb-bbbb-bbbb-222222222222");
    private static final UUID COMMUNITY_ID = UUID.fromString("33333333-cccc-cccc-cccc-333333333333");

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommunityMapper communityMapper;
    @Mock private PostMapper postMapper;
    @Mock private CommunityDeletionService communityDeletionService;

    @Test
    void createCommunity_validRequest_createsCommunityAndOwnerMembership() {
        UserEntity owner = user(OWNER_ID, "owner");
        CommunityEntity savedCommunity = community(owner);
        CommunityResponse expected = communityResponse(true, 1);

        when(userRepository.findByKeycloakId(OWNER_ID)).thenReturn(Optional.of(owner));
        when(communityRepository.existsByCommunityNameIgnoreCase("Бег по утрам")).thenReturn(false);
        when(communityRepository.save(any(CommunityEntity.class))).thenReturn(savedCommunity);
        when(communityMemberRepository.countByCommunity_Id(COMMUNITY_ID)).thenReturn(1L);
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, OWNER_ID))
                .thenReturn(true);
        when(communityMapper.toCommunityResponse(savedCommunity, 1L, true)).thenReturn(expected);

        CommunityResponse result = service().createCommunity(
                new CommunityCreateRequest("Бег по утрам", "Утренние пробежки"), OWNER_ID);

        ArgumentCaptor<CommunityMemberEntity> memberCaptor = ArgumentCaptor.forClass(CommunityMemberEntity.class);
        verify(communityMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getCommunity()).isSameAs(savedCommunity);
        assertThat(memberCaptor.getValue().getUser()).isSameAs(owner);
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(CommunityRole.OWNER);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void createCommunity_duplicateName_throwsConflict() {
        when(userRepository.findByKeycloakId(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "owner")));
        when(communityRepository.existsByCommunityNameIgnoreCase("Бег")).thenReturn(true);

        assertThatThrownBy(() -> service().createCommunity(
                new CommunityCreateRequest("Бег", null), OWNER_ID))
                .isInstanceOf(CommunityAlreadyExistsException.class);

        verify(communityRepository, never()).save(any());
    }

    @Test
    void updateCommunity_foreignUser_throwsAccessDenied() {
        CommunityEntity community = community(user(OWNER_ID, "owner"));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));

        assertThatThrownBy(() -> service().updateCommunity(
                COMMUNITY_ID,
                MEMBER_ID,
                new CommunityUpdateRequest("Новое имя", null)
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void joinCommunity_validUser_createsMemberRole() {
        CommunityEntity community = community(user(OWNER_ID, "owner"));
        UserEntity member = user(MEMBER_ID, "member");
        CommunityResponse expected = communityResponse(true, 2);

        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(userRepository.findByKeycloakId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, MEMBER_ID))
                .thenReturn(false, true);
        when(communityMemberRepository.countByCommunity_Id(COMMUNITY_ID)).thenReturn(2L);
        when(communityMapper.toCommunityResponse(community, 2L, true)).thenReturn(expected);

        CommunityResponse result = service().joinCommunity(COMMUNITY_ID, MEMBER_ID);

        ArgumentCaptor<CommunityMemberEntity> captor = ArgumentCaptor.forClass(CommunityMemberEntity.class);
        verify(communityMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(CommunityRole.MEMBER);
        assertThat(captor.getValue().getUser()).isSameAs(member);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void leaveCommunity_owner_throwsBadRequest() {
        CommunityEntity community = community(user(OWNER_ID, "owner"));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));

        assertThatThrownBy(() -> service().leaveCommunity(COMMUNITY_ID, OWNER_ID))
                .isInstanceOf(BadRequestException.class);

        verify(communityMemberRepository, never())
                .deleteByCommunity_IdAndUser_KeycloakId(any(), any());
    }

    @Test
    void createCommunityTextPost_nonMember_throwsAccessDenied() {
        CommunityEntity community = community(user(OWNER_ID, "owner"));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(userRepository.findByKeycloakId(MEMBER_ID)).thenReturn(Optional.of(user(MEMBER_ID, "member")));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, MEMBER_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service().createCommunityTextPost(
                COMMUNITY_ID,
                MEMBER_ID,
                new CommunityPostCreateRequest("Встреча", "Кто идет на пробежку?")
        )).isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    void createCommunityTextPost_member_createsCommunityVisibilityPost() {
        UserEntity owner = user(OWNER_ID, "owner");
        UserEntity member = user(MEMBER_ID, "member");
        CommunityEntity community = community(owner);
        PostResponse expected = mock(PostResponse.class);

        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(userRepository.findByKeycloakId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, MEMBER_ID))
                .thenReturn(true);
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postMapper.toPostResponse(any(PostEntity.class))).thenReturn(expected);

        PostResponse result = service().createCommunityTextPost(
                COMMUNITY_ID,
                MEMBER_ID,
                new CommunityPostCreateRequest("Встреча", "Кто идет на пробежку?")
        );

        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postRepository).save(captor.capture());
        PostEntity post = captor.getValue();
        assertThat(post.getUser()).isSameAs(member);
        assertThat(post.getCommunity()).isSameAs(community);
        assertThat(post.getVisibility()).isEqualTo(PostVisibility.COMMUNITY);
        assertThat(post.getPostType()).isEqualTo(PostType.TEXT);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void deleteCommunity_owner_delegatesToDeletionService() {
        CommunityEntity community = community(user(OWNER_ID, "owner"));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));

        service().deleteCommunity(COMMUNITY_ID, OWNER_ID);

        verify(communityDeletionService).deleteCommunityWithRelations(community);
    }

    @Test
    void getCommunityMembers_nonMember_throwsAccessDeniedBeforeReadingMembers() {
        CommunityEntity community = community(user(OWNER_ID, "owner"));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, MEMBER_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service().getCommunityMembers(COMMUNITY_ID, MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(communityMemberRepository, never())
                .findAllByCommunity_IdOrderByJoinedAtDesc(COMMUNITY_ID);
    }

    @Test
    void getCommunityPosts_nonMember_throwsAccessDeniedBeforeReadingPosts() {
        CommunityEntity community = community(user(OWNER_ID, "owner"));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, MEMBER_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service().getCommunityPosts(COMMUNITY_ID, MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never())
                .findAllByCommunity_IdOrderByCreatedAtDesc(COMMUNITY_ID);
    }

    private CommunityService service() {
        return new CommunityService(
                communityRepository,
                communityMemberRepository,
                userRepository,
                postRepository,
                communityMapper,
                postMapper,
                communityDeletionService
        );
    }

    private UserEntity user(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setKeycloakId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }

    private CommunityEntity community(UserEntity owner) {
        CommunityEntity community = new CommunityEntity();
        community.setId(COMMUNITY_ID);
        community.setOwner(owner);
        community.setCommunityName("Бег по утрам");
        community.setDescription("Описание");
        community.setCreatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        community.setUpdatedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        return community;
    }

    private CommunityResponse communityResponse(boolean member, long count) {
        return new CommunityResponse(
                COMMUNITY_ID,
                OWNER_ID,
                "Бег по утрам",
                "Описание",
                count,
                member,
                LocalDateTime.of(2026, 7, 14, 10, 0),
                LocalDateTime.of(2026, 7, 14, 10, 0)
        );
    }
}
