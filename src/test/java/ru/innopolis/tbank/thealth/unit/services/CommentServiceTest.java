package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.innopolis.tbank.thealth.dto.request.CommentCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.CommentResponse;
import ru.innopolis.tbank.thealth.entities.CommentEntity;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.exceptions.CommunityMemberNotFoundException;
import ru.innopolis.tbank.thealth.mappers.CommunityMapper;
import ru.innopolis.tbank.thealth.repositories.CommentRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.CommentService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final UUID POST_ID = UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111");
    private static final UUID COMMENT_ID = UUID.fromString("22222222-bbbb-bbbb-bbbb-222222222222");
    private static final UUID AUTHOR_ID = UUID.fromString("33333333-cccc-cccc-cccc-333333333333");
    private static final UUID POST_AUTHOR_ID = UUID.fromString("44444444-dddd-dddd-dddd-444444444444");
    private static final UUID STRANGER_ID = UUID.fromString("55555555-eeee-eeee-eeee-555555555555");
    private static final UUID COMMUNITY_ID = UUID.fromString("66666666-ffff-ffff-ffff-666666666666");

    @Mock private CommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private CommunityMapper communityMapper;

    @Test
    void createComment_publicPost_savesCommentWithoutMembershipCheck() {
        PostEntity post = publicPost();
        UserEntity author = user(AUTHOR_ID, "commenter");
        CommentResponse expected = response();

        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(userRepository.findByKeycloakId(AUTHOR_ID)).thenReturn(Optional.of(author));
        when(commentRepository.save(any(CommentEntity.class))).thenAnswer(invocation -> {
            CommentEntity saved = invocation.getArgument(0);
            saved.setId(COMMENT_ID);
            saved.setCreatedAt(LocalDateTime.of(2026, 7, 14, 11, 0));
            return saved;
        });
        when(communityMapper.toCommentResponse(any(CommentEntity.class))).thenReturn(expected);

        CommentResponse result = service().createComment(
                POST_ID,
                AUTHOR_ID,
                new CommentCreateRequest("Отличный пост")
        );

        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getPost()).isSameAs(post);
        assertThat(captor.getValue().getAuthor()).isSameAs(author);
        assertThat(captor.getValue().getContent()).isEqualTo("Отличный пост");
        verifyNoInteractions(communityMemberRepository);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void getPostComments_communityPostNonMember_throwsNotFound() {
        PostEntity post = communityPost();
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, STRANGER_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service().getPostComments(POST_ID, STRANGER_ID))
                .isInstanceOf(CommunityMemberNotFoundException.class);

        verify(commentRepository, never()).findAllByPost_IdOrderByCreatedAtAsc(any());
    }

    @Test
    void getPostComments_communityMember_mapsComments() {
        PostEntity post = communityPost();
        CommentEntity comment = comment(post, user(AUTHOR_ID, "commenter"));
        CommentResponse expected = response();

        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, AUTHOR_ID))
                .thenReturn(true);
        when(commentRepository.findAllByPost_IdOrderByCreatedAtAsc(POST_ID)).thenReturn(List.of(comment));
        when(communityMapper.toCommentResponse(comment)).thenReturn(expected);

        List<CommentResponse> result = service().getPostComments(POST_ID, AUTHOR_ID);

        assertThat(result).containsExactly(expected);
    }

    @Test
    void deleteComment_postAuthor_canDeleteForeignComment() {
        PostEntity post = publicPost();
        CommentEntity comment = comment(post, user(AUTHOR_ID, "commenter"));
        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID)).thenReturn(Optional.of(comment));

        service().deleteComment(POST_ID, COMMENT_ID, POST_AUTHOR_ID);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_stranger_throwsAccessDenied() {
        PostEntity post = publicPost();
        CommentEntity comment = comment(post, user(AUTHOR_ID, "commenter"));
        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service().deleteComment(POST_ID, COMMENT_ID, STRANGER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_communityOwner_canDeleteComment() {
        PostEntity post = communityPost();
        UUID ownerId = post.getCommunity().getOwner().getKeycloakId();
        CommentEntity comment = comment(post, user(AUTHOR_ID, "commenter"));
        when(commentRepository.findByIdAndPost_Id(COMMENT_ID, POST_ID)).thenReturn(Optional.of(comment));
        when(communityMemberRepository.existsByCommunity_IdAndUser_KeycloakId(COMMUNITY_ID, ownerId))
                .thenReturn(true);

        service().deleteComment(POST_ID, COMMENT_ID, ownerId);

        verify(commentRepository).delete(comment);
    }

    private CommentService service() {
        return new CommentService(
                commentRepository,
                postRepository,
                userRepository,
                communityMemberRepository,
                communityMapper
        );
    }

    private PostEntity publicPost() {
        PostEntity post = new PostEntity();
        post.setId(POST_ID);
        post.setUser(user(POST_AUTHOR_ID, "post-author"));
        post.setPostType(PostType.TEXT);
        post.setVisibility(PostVisibility.PUBLIC);
        post.setTitle("Публичный пост");
        post.setContent("Содержание");
        return post;
    }

    private PostEntity communityPost() {
        UserEntity owner = user(UUID.fromString("77777777-aaaa-bbbb-cccc-777777777777"), "owner");
        CommunityEntity community = new CommunityEntity();
        community.setId(COMMUNITY_ID);
        community.setOwner(owner);
        community.setCommunityName("Бег");

        PostEntity post = publicPost();
        post.setCommunity(community);
        post.setVisibility(PostVisibility.COMMUNITY);
        return post;
    }

    private CommentEntity comment(PostEntity post, UserEntity author) {
        CommentEntity comment = new CommentEntity();
        comment.setId(COMMENT_ID);
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent("Комментарий");
        comment.setCreatedAt(LocalDateTime.of(2026, 7, 14, 11, 0));
        return comment;
    }

    private UserEntity user(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setKeycloakId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }

    private CommentResponse response() {
        return new CommentResponse(
                COMMENT_ID,
                POST_ID,
                AUTHOR_ID,
                "commenter",
                "Отличный пост",
                LocalDateTime.of(2026, 7, 14, 11, 0)
        );
    }
}
