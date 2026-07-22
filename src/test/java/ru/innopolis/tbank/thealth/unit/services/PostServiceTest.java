package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.innopolis.tbank.thealth.dto.request.PostInfoRequest;
import ru.innopolis.tbank.thealth.dto.request.TextPostCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.exceptions.ConflictException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.AchievementMapper;
import ru.innopolis.tbank.thealth.mappers.PostMapper;
import ru.innopolis.tbank.thealth.mappers.RecipeMapper;
import ru.innopolis.tbank.thealth.mappers.WorkoutMapper;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.RecipeRepository;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;
import ru.innopolis.tbank.thealth.services.PostDeletionService;
import ru.innopolis.tbank.thealth.services.PostService;
import ru.innopolis.tbank.thealth.services.RecipeService;
import ru.innopolis.tbank.thealth.services.WorkoutService;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private UserAchievementRepository userAchievementRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private WorkoutService workoutService;
    @Mock
    private RecipeService recipeService;
    @Mock
    private PostDeletionService postDeletionService;

    private PostService postService;

    @BeforeEach
    void setUp() {
        PostMapper postMapper = new PostMapper(
                new WorkoutMapper(),
                new RecipeMapper(),
                new AchievementMapper()
        );
        postService = new PostService(
                userAchievementRepository,
                userRepository,
                workoutRepository,
                postRepository,
                recipeRepository,
                postMapper,
                workoutService,
                recipeService,
                postDeletionService
        );
    }

    @Test
    @DisplayName("Текстовый пост создаётся публичным и без связанных сущностей")
    void createTextPost_shouldCreatePublicTextPost() {
        UUID userId = TestFixtures.USER_ID;
        UserEntity user = TestFixtures.user(userId, "george");
        Jwt jwt = TestFixtures.jwt(userId);

        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.of(user));
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> {
            PostEntity post = invocation.getArgument(0);
            post.setId(UUID.randomUUID());
            post.setCreatedAt(LocalDateTime.now());
            post.setUpdatedAt(LocalDateTime.now());
            return post;
        });

        PostResponse response = postService.createTextPost(
                jwt,
                new TextPostCreateRequest(new PostInfoRequest("My progress"), "First week completed")
        );

        ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
        verify(postRepository).save(captor.capture());
        PostEntity saved = captor.getValue();

        assertThat(saved.getPostType()).isEqualTo(PostType.TEXT);
        assertThat(saved.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(saved.getWorkout()).isNull();
        assertThat(saved.getRecipe()).isNull();
        assertThat(response.authorId()).isEqualTo(userId);
        assertThat(response.content()).isEqualTo("First week completed");
    }

    @Test
    @DisplayName("Нельзя повторно опубликовать одну тренировку")
    void postWorkout_whenAlreadyPublished_shouldThrowConflict() {
        UUID userId = TestFixtures.USER_ID;
        UUID workoutId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(userId, "george");
        WorkoutEntity workout = TestFixtures.workout(workoutId, user);

        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.of(user));
        when(workoutRepository.findByIdAndUser_KeycloakId(workoutId, userId))
                .thenReturn(Optional.of(workout));
        when(postRepository.existsByWorkout_Id(workoutId)).thenReturn(true);

        assertThatThrownBy(() -> postService.postWorkout(
                workoutId,
                TestFixtures.jwt(userId),
                new PostInfoRequest("Morning workout")
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already published");

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("Публикация тренировки создаёт WORKOUT-пост с владельцем")
    void postWorkout_shouldCreateWorkoutPost() {
        UUID userId = TestFixtures.USER_ID;
        UUID workoutId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(userId, "george");
        WorkoutEntity workout = TestFixtures.workout(workoutId, user);

        when(userRepository.findByKeycloakId(userId)).thenReturn(Optional.of(user));
        when(workoutRepository.findByIdAndUser_KeycloakId(workoutId, userId))
                .thenReturn(Optional.of(workout));
        when(postRepository.existsByWorkout_Id(workoutId)).thenReturn(false);
        when(postRepository.save(any(PostEntity.class))).thenAnswer(invocation -> {
            PostEntity post = invocation.getArgument(0);
            post.setId(UUID.randomUUID());
            post.setCreatedAt(LocalDateTime.now());
            post.setUpdatedAt(LocalDateTime.now());
            return post;
        });

        PostResponse response = postService.postWorkout(
                workoutId,
                TestFixtures.jwt(userId),
                new PostInfoRequest("Morning workout")
        );

        assertThat(response.type()).isEqualTo(PostType.WORKOUT);
        assertThat(response.visibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(response.workout().id()).isEqualTo(workoutId);
    }

    @Test
    @DisplayName("Лента без фильтра читает все публичные посты")
    void getPosts_withoutFilter_shouldUsePublicFeedQuery() {
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        PostEntity post = TestFixtures.textPost(UUID.randomUUID(), user);
        when(postRepository.findAllByVisibilityOrderByCreatedAtDesc(PostVisibility.PUBLIC))
                .thenReturn(List.of(post));

        List<PostResponse> result = postService.getPosts(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(PostType.TEXT);
        verify(postRepository, never())
                .findAllByVisibilityAndPostTypeOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("Лента с фильтром использует тип поста")
    void getPosts_withFilter_shouldUseTypedQuery() {
        when(postRepository.findAllByVisibilityAndPostTypeOrderByCreatedAtDesc(
                PostVisibility.PUBLIC, PostType.RECIPE
        )).thenReturn(List.of());

        List<PostResponse> result = postService.getPosts(PostType.RECIPE);

        assertThat(result).isEmpty();
        verify(postRepository).findAllByVisibilityAndPostTypeOrderByCreatedAtDesc(
                PostVisibility.PUBLIC, PostType.RECIPE
        );
    }

    @Test
    @DisplayName("Удаление поста делегируется PostDeletionService с id текущего пользователя")
    void deletePost_shouldDelegateToPostDeletionService() {
        UUID postId = UUID.randomUUID();

        postService.deletePostById(TestFixtures.jwt(TestFixtures.USER_ID), postId);

        verify(postDeletionService).deleteOwnedPost(postId, TestFixtures.USER_ID);
    }

    @Test
    @DisplayName("Операции пользователя требуют локального профиля")
    void createTextPost_whenUserMissing_shouldThrow() {
        when(userRepository.findByKeycloakId(TestFixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createTextPost(
                TestFixtures.jwt(TestFixtures.USER_ID),
                new TextPostCreateRequest(new PostInfoRequest("Title"), "Content")
        )).isInstanceOf(UserNotFoundException.class);
    }
}
