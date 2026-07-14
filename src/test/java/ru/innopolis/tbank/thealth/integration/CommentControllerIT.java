package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.entities.CommentEntity;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.entities.CommunityMemberEntity;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.enums.CommunityRole;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.repositories.CommentRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentControllerIT extends AbstractIntegrationTest {

    private static final UUID POST_AUTHOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID COMMENT_AUTHOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER_ID = UUID.fromString("40000000-0000-0000-0000-000000000003");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Test
    @DisplayName("Авторизованный пользователь может комментировать PUBLIC-пост")
    void createComment_publicPost_returnsCreated() throws Exception {
        UserEntity postAuthor = persistUser(POST_AUTHOR_ID, "post-author");
        persistUser(COMMENT_AUTHOR_ID, "comment-author");
        PostEntity post = savePost(postAuthor, PostVisibility.PUBLIC, null);

        mockMvc.perform(post("/api/posts/{postId}/comments", post.getId())
                        .with(jwtFor(COMMENT_AUTHOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Отличная тренировка!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(post.getId().toString()))
                .andExpect(jsonPath("$.authorId").value(COMMENT_AUTHOR_ID.toString()))
                .andExpect(jsonPath("$.content").value("Отличная тренировка!"));

        assertThat(commentRepository.findAllByPost_IdOrderByCreatedAtAsc(post.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Пустой комментарий возвращает 400")
    void createComment_blankContent_returnsBadRequest() throws Exception {
        UserEntity postAuthor = persistUser(POST_AUTHOR_ID, "post-author");
        persistUser(COMMENT_AUTHOR_ID, "comment-author");
        PostEntity post = savePost(postAuthor, PostVisibility.PUBLIC, null);

        mockMvc.perform(post("/api/posts/{postId}/comments", post.getId())
                        .with(jwtFor(COMMENT_AUTHOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Неучастник не может читать комментарии COMMUNITY-поста")
    void getCommunityComments_nonMember_returnsNotFound() throws Exception {
        UserEntity owner = persistUser(POST_AUTHOR_ID, "community-owner");
        persistUser(STRANGER_ID, "community-stranger");
        CommunityEntity community = saveCommunity(owner, "Закрытый клуб");
        PostEntity post = savePost(owner, PostVisibility.COMMUNITY, community);

        mockMvc.perform(get("/api/posts/{postId}/comments", post.getId())
                        .with(jwtFor(STRANGER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Автор поста может удалить чужой комментарий")
    void deleteComment_postAuthor_canDeleteComment() throws Exception {
        UserEntity postAuthor = persistUser(POST_AUTHOR_ID, "post-author");
        UserEntity commentAuthor = persistUser(COMMENT_AUTHOR_ID, "comment-author");
        PostEntity post = savePost(postAuthor, PostVisibility.PUBLIC, null);
        CommentEntity comment = saveComment(post, commentAuthor, "Комментарий");

        mockMvc.perform(delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .with(jwtFor(POST_AUTHOR_ID)))
                .andExpect(status().isNoContent());

        assertThat(commentRepository.findById(comment.getId())).isEmpty();
    }

    @Test
    @DisplayName("Посторонний пользователь не может удалить комментарий")
    void deleteComment_stranger_returnsForbidden() throws Exception {
        UserEntity postAuthor = persistUser(POST_AUTHOR_ID, "post-author");
        UserEntity commentAuthor = persistUser(COMMENT_AUTHOR_ID, "comment-author");
        persistUser(STRANGER_ID, "stranger");
        PostEntity post = savePost(postAuthor, PostVisibility.PUBLIC, null);
        CommentEntity comment = saveComment(post, commentAuthor, "Комментарий");

        mockMvc.perform(delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .with(jwtFor(STRANGER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private CommunityEntity saveCommunity(UserEntity owner, String name) {
        CommunityEntity community = new CommunityEntity();
        community.setOwner(owner);
        community.setCommunityName(name);
        community.setDescription("Тестовое сообщество");
        CommunityEntity saved = communityRepository.saveAndFlush(community);

        CommunityMemberEntity member = new CommunityMemberEntity();
        member.setCommunity(saved);
        member.setUser(owner);
        member.setRole(CommunityRole.OWNER);
        communityMemberRepository.saveAndFlush(member);
        return saved;
    }

    private PostEntity savePost(UserEntity author, PostVisibility visibility, CommunityEntity community) {
        PostEntity post = new PostEntity();
        post.setUser(author);
        post.setPostType(PostType.TEXT);
        post.setVisibility(visibility);
        post.setCommunity(community);
        post.setTitle("Тестовый пост");
        post.setContent("Содержание тестового поста");
        return postRepository.saveAndFlush(post);
    }

    private CommentEntity saveComment(PostEntity post, UserEntity author, String content) {
        CommentEntity comment = new CommentEntity();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(content);
        return commentRepository.saveAndFlush(comment);
    }
}
