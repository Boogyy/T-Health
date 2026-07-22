package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.exceptions.PostNotFoundException;
import ru.innopolis.tbank.thealth.repositories.CommentRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.services.PostDeletionService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostDeletionServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Test
    void deleteOwnedPost_existingPost_deletesCommentsBeforePost() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PostEntity post = post(postId);

        when(postRepository.findByIdAndUser_KeycloakId(postId, userId))
                .thenReturn(Optional.of(post));

        PostDeletionService service =
                new PostDeletionService(postRepository, commentRepository);

        service.deleteOwnedPost(postId, userId);

        InOrder inOrder = inOrder(commentRepository, postRepository);
        inOrder.verify(commentRepository).deleteAllByPost_Id(postId);
        inOrder.verify(postRepository).delete(post);
    }

    @Test
    void deleteOwnedPost_missingPost_throwsNotFoundAndDeletesNothing() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(postRepository.findByIdAndUser_KeycloakId(postId, userId))
                .thenReturn(Optional.empty());

        PostDeletionService service =
                new PostDeletionService(postRepository, commentRepository);

        assertThatThrownBy(() -> service.deleteOwnedPost(postId, userId))
                .isInstanceOf(PostNotFoundException.class);

        verifyNoInteractions(commentRepository);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void deleteAllPostsByUser_deletesEveryPostWithComments() {
        UUID userId = UUID.randomUUID();
        PostEntity first = post(UUID.randomUUID());
        PostEntity second = post(UUID.randomUUID());

        when(postRepository.findAllByUser_KeycloakId(userId))
                .thenReturn(List.of(first, second));

        PostDeletionService service =
                new PostDeletionService(postRepository, commentRepository);

        service.deleteAllPostsByUser(userId);

        InOrder inOrder = inOrder(commentRepository, postRepository);
        inOrder.verify(commentRepository).deleteAllByPost_Id(first.getId());
        inOrder.verify(postRepository).delete(first);
        inOrder.verify(commentRepository).deleteAllByPost_Id(second.getId());
        inOrder.verify(postRepository).delete(second);
    }

    @Test
    void deleteAllPostsByCommunity_deletesEveryPostWithComments() {
        UUID communityId = UUID.randomUUID();
        PostEntity first = post(UUID.randomUUID());
        PostEntity second = post(UUID.randomUUID());

        when(postRepository.findAllByCommunity_Id(communityId))
                .thenReturn(List.of(first, second));

        PostDeletionService service =
                new PostDeletionService(postRepository, commentRepository);

        service.deleteAllPostsByCommunity(communityId);

        verify(commentRepository).deleteAllByPost_Id(first.getId());
        verify(commentRepository).deleteAllByPost_Id(second.getId());
        verify(postRepository).delete(first);
        verify(postRepository).delete(second);
    }

    private PostEntity post(UUID id) {
        PostEntity post = new PostEntity();
        post.setId(id);
        return post;
    }
}
