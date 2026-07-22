package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.exceptions.PostNotFoundException;
import ru.innopolis.tbank.thealth.repositories.CommentRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;

import java.util.List;
import java.util.UUID;

@Service
public class PostDeletionService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public PostDeletionService(PostRepository postRepository,
                               CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * Удаляет конкретный пост пользователя вместе с комментариями.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteOwnedPost(UUID postId, UUID userId) {
        PostEntity post = postRepository
                .findByIdAndUser_KeycloakId(postId, userId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        deletePostWithComments(post);
    }

    /**
     * Удаляет все посты пользователя вместе с комментариями под ними.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllPostsByUser(UUID userId) {
        List<PostEntity> posts =
                postRepository.findAllByUser_KeycloakId(userId);

        posts.forEach(this::deletePostWithComments);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllPostsByCommunity(UUID communityId) {
        List<PostEntity> posts =
                postRepository.findAllByCommunity_Id(communityId);

        posts.forEach(this::deletePostWithComments);
    }

    private void deletePostWithComments(PostEntity post) {
        commentRepository.deleteAllByPost_Id(post.getId());
        postRepository.delete(post);
    }
}