package ru.innopolis.tbank.thealth.services;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.CommentCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.CommentResponse;
import ru.innopolis.tbank.thealth.entities.CommentEntity;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.exceptions.CommentNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.CommunityMemberNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.PostNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.CommunityMapper;
import ru.innopolis.tbank.thealth.repositories.CommentRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityMapper communityMapper;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          UserRepository userRepository,
                          CommunityMemberRepository communityMemberRepository,
                          CommunityMapper communityMapper) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.communityMapper = communityMapper;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getPostComments(UUID postId, UUID currentUserId) {
        PostEntity post = findPost(postId);
        checkPostAccess(post, currentUserId);

        return commentRepository.findAllByPost_IdOrderByCreatedAtAsc(postId)
                .stream()
                .map(communityMapper::toCommentResponse)
                .toList();
    }

    @Transactional
    public CommentResponse createComment(
            UUID postId,
            UUID currentUserId,
            CommentCreateRequest request
    ) {
        PostEntity post = findPost(postId);
        UserEntity author = findUser(currentUserId);

        checkPostAccess(post, currentUserId);

        CommentEntity comment = new CommentEntity();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(request.content());

        CommentEntity savedComment = commentRepository.save(comment);

        return communityMapper.toCommentResponse(savedComment);
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID currentUserId) {
        CommentEntity comment = commentRepository.findByIdAndPost_Id(commentId, postId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        PostEntity post = comment.getPost();

        checkPostAccess(post, currentUserId);
        checkCanDeleteComment(comment, currentUserId);

        commentRepository.delete(comment);
    }

    private PostEntity findPost(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findByKeycloakId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void checkPostAccess(PostEntity post, UUID currentUserId) {
        if (post.getVisibility() != PostVisibility.COMMUNITY) {
            return;
        }

        UUID communityId = post.getCommunity().getId();

        boolean currentUserMember = communityMemberRepository
                .existsByCommunity_IdAndUser_KeycloakId(communityId, currentUserId);

        if (!currentUserMember) {
            throw new CommunityMemberNotFoundException(communityId);
        }
    }

    private void checkCanDeleteComment(CommentEntity comment, UUID currentUserId) {
        PostEntity post = comment.getPost();

        boolean commentAuthor = comment.getAuthor().getKeycloakId().equals(currentUserId);
        boolean postAuthor = post.getUser().getKeycloakId().equals(currentUserId);
        boolean communityOwner = post.getCommunity() != null
                && post.getCommunity().getOwner().getKeycloakId().equals(currentUserId);

        if (!commentAuthor && !postAuthor && !communityOwner) {
            throw new AccessDeniedException("Only comment author, post author or community owner can delete comment");
        }
    }
}