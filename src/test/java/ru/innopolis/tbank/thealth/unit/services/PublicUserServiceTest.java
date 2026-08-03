package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.dto.response.PublicCommunityResponse;
import ru.innopolis.tbank.thealth.dto.response.PublicUserProfileResponse;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.entities.CommunityMemberEntity;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.enums.CommunityRole;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.PostMapper;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.PublicUserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicUserServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("22222222-aaaa-bbbb-cccc-222222222222");
    private static final UUID POST_ID =
            UUID.fromString("33333333-aaaa-bbbb-cccc-333333333333");
    private static final UUID COMMUNITY_ID =
            UUID.fromString("44444444-aaaa-bbbb-cccc-444444444444");

    private static final String USERNAME = "DoubleCheck";

    private static final LocalDateTime USER_CREATED_AT =
            LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime POST_CREATED_AT =
            LocalDateTime.of(2026, 8, 2, 11, 30);
    private static final LocalDateTime JOINED_AT =
            LocalDateTime.of(2026, 8, 2, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommunityMemberRepository communityMemberRepository;

    @Mock
    private PostMapper postMapper;

    private PublicUserService publicUserService;

    @BeforeEach
    void setUp() {
        publicUserService = new PublicUserService(
                userRepository,
                postRepository,
                communityMemberRepository,
                postMapper
        );
    }

    @Test
    @DisplayName("Публичный профиль возвращает пользователя, PUBLIC-публикации и сообщества")
    void getPublicProfile_existingUser_returnsPublicData() {
        UserEntity user = user();
        PostEntity publicPost = publicTextPost(user);
        PostResponse mappedPost = mappedPost();
        CommunityMemberEntity membership = membership(user);

        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(user));
        when(postRepository
                .findAllByUser_KeycloakIdAndVisibilityOrderByCreatedAtDesc(
                        USER_ID,
                        PostVisibility.PUBLIC
                ))
                .thenReturn(List.of(publicPost));
        when(postMapper.toPostResponse(publicPost))
                .thenReturn(mappedPost);
        when(communityMemberRepository
                .findAllByUser_KeycloakIdOrderByJoinedAtDesc(USER_ID))
                .thenReturn(List.of(membership));

        PublicUserProfileResponse response =
                publicUserService.getPublicProfile(USERNAME);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.username()).isEqualTo(USERNAME);
        assertThat(response.firstName()).isEqualTo("Ivan");
        assertThat(response.lastName()).isEqualTo("Ivanov");
        assertThat(response.memberSince()).isEqualTo(USER_CREATED_AT);
        assertThat(response.publications()).containsExactly(mappedPost);

        assertThat(response.communities()).containsExactly(
                new PublicCommunityResponse(
                        COMMUNITY_ID,
                        "Бег по утрам",
                        "Сообщество любителей утренних пробежек",
                        CommunityRole.MEMBER,
                        JOINED_AT
                )
        );

        verify(userRepository).findByUsername(USERNAME);
        verify(postRepository)
                .findAllByUser_KeycloakIdAndVisibilityOrderByCreatedAtDesc(
                        USER_ID,
                        PostVisibility.PUBLIC
                );
        verify(postMapper).toPostResponse(publicPost);
        verify(communityMemberRepository)
                .findAllByUser_KeycloakIdOrderByJoinedAtDesc(USER_ID);
    }

    @Test
    @DisplayName("Публичный профиль ищется по username с учётом регистра")
    void getPublicProfile_differentUsernameCase_throwsNotFound() {
        String differentCase = "doublecheck";

        when(userRepository.findByUsername(differentCase))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                publicUserService.getPublicProfile(differentCase)
        ).isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findByUsername(differentCase);
        verifyNoInteractions(
                postRepository,
                communityMemberRepository,
                postMapper
        );
    }

    @Test
    @DisplayName("Для неизвестного username сервис не загружает публикации и сообщества")
    void getPublicProfile_unknownUsername_throwsNotFoundWithoutExtraQueries() {
        String unknownUsername = "unknown-user";

        when(userRepository.findByUsername(unknownUsername))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                publicUserService.getPublicProfile(unknownUsername)
        ).isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findByUsername(unknownUsername);
        verifyNoInteractions(
                postRepository,
                communityMemberRepository,
                postMapper
        );
    }

    @Test
    @DisplayName("Профиль без публикаций и сообществ возвращает пустые списки")
    void getPublicProfile_withoutPublicData_returnsEmptyCollections() {
        UserEntity user = user();

        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(user));
        when(postRepository
                .findAllByUser_KeycloakIdAndVisibilityOrderByCreatedAtDesc(
                        USER_ID,
                        PostVisibility.PUBLIC
                ))
                .thenReturn(List.of());
        when(communityMemberRepository
                .findAllByUser_KeycloakIdOrderByJoinedAtDesc(USER_ID))
                .thenReturn(List.of());

        PublicUserProfileResponse response =
                publicUserService.getPublicProfile(USERNAME);

        assertThat(response.publications()).isEmpty();
        assertThat(response.communities()).isEmpty();

        verify(postMapper, never()).toPostResponse(org.mockito.ArgumentMatchers.any());
    }

    private UserEntity user() {
        return new UserEntity(
                USER_ID,
                USERNAME,
                "doublecheck@example.com",
                "Ivan",
                "Ivanov",
                USER_CREATED_AT,
                USER_CREATED_AT
        );
    }

    private PostEntity publicTextPost(UserEntity user) {
        PostEntity post = new PostEntity();
        post.setId(POST_ID);
        post.setUser(user);
        post.setVisibility(PostVisibility.PUBLIC);
        post.setPostType(PostType.TEXT);
        post.setTitle("Публичная публикация");
        post.setContent("Текст публичной публикации");
        post.setCreatedAt(POST_CREATED_AT);
        post.setUpdatedAt(POST_CREATED_AT);
        return post;
    }

    private PostResponse mappedPost() {
        return new PostResponse(
                POST_ID,
                USER_ID,
                USERNAME,
                null,
                "Публичная публикация",
                PostVisibility.PUBLIC,
                null,
                null,
                null,
                PostType.TEXT,
                "Текст публичной публикации",
                POST_CREATED_AT,
                POST_CREATED_AT
        );
    }

    private CommunityMemberEntity membership(UserEntity user) {
        CommunityEntity community = new CommunityEntity();
        community.setId(COMMUNITY_ID);
        community.setOwner(user);
        community.setCommunityName("Бег по утрам");
        community.setDescription(
                "Сообщество любителей утренних пробежек"
        );

        CommunityMemberEntity membership = new CommunityMemberEntity();
        membership.setCommunity(community);
        membership.setUser(user);
        membership.setRole(CommunityRole.MEMBER);
        membership.setJoinedAt(JOINED_AT);
        return membership;
    }
}