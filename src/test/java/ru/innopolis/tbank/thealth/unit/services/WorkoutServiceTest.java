package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.innopolis.tbank.thealth.dto.request.WorkoutCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.WorkoutUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.WorkoutResponse;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.WorkoutType;
import ru.innopolis.tbank.thealth.exceptions.ConflictException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.WorkoutNotFoundException;
import ru.innopolis.tbank.thealth.mappers.WorkoutMapper;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;
import ru.innopolis.tbank.thealth.services.AchievementService;
import ru.innopolis.tbank.thealth.services.PostDeletionService;
import ru.innopolis.tbank.thealth.services.WorkoutService;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private AchievementService achievementService;
    @Mock
    private PostRepository postRepository;
    @Mock
    private PostDeletionService postDeletionService;

    private WorkoutService workoutService;

    @BeforeEach
    void setUp() {
        workoutService = new WorkoutService(
                userRepository,
                workoutRepository,
                achievementService,
                new WorkoutMapper(),
                postRepository,
                postDeletionService
        );
    }

    @Test
    @DisplayName("Создание тренировки сохраняет владельца и выдаёт подходящие достижения")
    void createWorkout_shouldSaveWorkoutAndGrantAchievements() {
        UUID userId = TestFixtures.USER_ID;
        UserEntity user = TestFixtures.user(userId, "george");
        WorkoutCreateRequest request = new WorkoutCreateRequest(
                "Long run",
                WorkoutType.CARDIO,
                "Tempo run",
                60,
                500
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(workoutRepository.save(any(WorkoutEntity.class))).thenAnswer(invocation -> {
            WorkoutEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setWorkoutDate(LocalDateTime.now());
            return entity;
        });
        when(workoutRepository.countByUser_KeycloakId(userId)).thenReturn(5L);

        WorkoutResponse response = workoutService.createWorkout(request, userId);

        ArgumentCaptor<WorkoutEntity> captor = ArgumentCaptor.forClass(WorkoutEntity.class);
        verify(workoutRepository).save(captor.capture());
        WorkoutEntity saved = captor.getValue();

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTitle()).isEqualTo("Long run");
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.type()).isEqualTo(WorkoutType.CARDIO);

        verify(achievementService).grantAchievementIfNotExists(userId, "FIRST_WORKOUT");
        verify(achievementService).grantAchievementIfNotExists(userId, "FIVE_WORKOUTS");
        verify(achievementService).grantAchievementIfNotExists(userId, "LONG_WORKOUT");
        verify(achievementService).grantAchievementIfNotExists(userId, "CALORIE_BURNER");
    }

    @Test
    @DisplayName("Создание тренировки для отсутствующего пользователя возвращает 404-исключение")
    void createWorkout_whenUserMissing_shouldThrow() {
        UUID userId = TestFixtures.USER_ID;
        WorkoutCreateRequest request = new WorkoutCreateRequest(
                "Run", WorkoutType.CARDIO, null, 30, 200
        );
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workoutService.createWorkout(request, userId))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(workoutRepository, achievementService);
    }

    @Test
    @DisplayName("PATCH тренировки изменяет только переданные поля")
    void updateWorkout_shouldChangeOnlyProvidedFields() {
        UUID workoutId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        WorkoutEntity workout = TestFixtures.workout(workoutId, user);
        String oldTitle = workout.getTitle();

        when(workoutRepository.findByIdAndUser_KeycloakId(workoutId, user.getKeycloakId()))
                .thenReturn(Optional.of(workout));

        WorkoutUpdateRequest request = new WorkoutUpdateRequest(
                null,
                WorkoutType.STRETCHING,
                "Mobility session",
                90,
                100
        );

        WorkoutResponse response = workoutService.updateWorkout(workoutId, user.getKeycloakId(), request);

        assertThat(workout.getTitle()).isEqualTo(oldTitle);
        assertThat(workout.getType()).isEqualTo(WorkoutType.STRETCHING);
        assertThat(workout.getDescription()).isEqualTo("Mobility session");
        assertThat(workout.getDurationMinutes()).isEqualTo(90);
        assertThat(response.id()).isEqualTo(workoutId);
        verify(workoutRepository, never()).save(any());
    }

    @Test
    @DisplayName("Опубликованную тренировку нельзя удалить без подтверждения")
    void deleteWorkout_whenPublishedAndNotConfirmed_shouldThrowConflict() {
        UUID workoutId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        WorkoutEntity workout = TestFixtures.workout(workoutId, user);
        PostEntity post = new PostEntity();

        when(workoutRepository.findByIdAndUser_KeycloakId(workoutId, user.getKeycloakId()))
                .thenReturn(Optional.of(workout));
        when(postRepository.findByWorkout_Id(workoutId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> workoutService.deleteWorkout(workoutId, user.getKeycloakId(), false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("published");

        verifyNoInteractions(postDeletionService);
        verify(workoutRepository, never()).delete(any());
    }

    @Test
    @DisplayName("При подтверждении удаление поста делегируется PostDeletionService, затем удаляется тренировка")
    void deleteWorkout_whenConfirmed_shouldDeletePostAndWorkout() {
        UUID workoutId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        WorkoutEntity workout = TestFixtures.workout(workoutId, user);
        PostEntity post = new PostEntity();
        post.setId(postId);

        when(workoutRepository.findByIdAndUser_KeycloakId(workoutId, user.getKeycloakId()))
                .thenReturn(Optional.of(workout));
        when(postRepository.findByWorkout_Id(workoutId)).thenReturn(Optional.of(post));

        workoutService.deleteWorkout(workoutId, user.getKeycloakId(), true);

        InOrder inOrder = inOrder(postDeletionService, workoutRepository);
        inOrder.verify(postDeletionService).deleteOwnedPost(postId, user.getKeycloakId());
        inOrder.verify(workoutRepository).delete(workout);
        verify(postRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Чужая тренировка маскируется как несуществующая")
    void getWorkout_whenNotOwned_shouldThrowNotFound() {
        UUID workoutId = UUID.randomUUID();
        when(workoutRepository.findByIdAndUser_KeycloakId(workoutId, TestFixtures.USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workoutService.getWorkout(workoutId, TestFixtures.USER_ID))
                .isInstanceOf(WorkoutNotFoundException.class);
    }
}
