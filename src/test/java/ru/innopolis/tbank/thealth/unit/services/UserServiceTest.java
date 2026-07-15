package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.innopolis.tbank.thealth.dto.request.UpdateUserRequest;
import ru.innopolis.tbank.thealth.dto.response.UserResponse;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.DuplicateUsernameException;
import ru.innopolis.tbank.thealth.exceptions.MissingTokenClaimException;
import ru.innopolis.tbank.thealth.repositories.FoodEntryRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.RecipeRepository;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;
import ru.innopolis.tbank.thealth.services.AchievementService;
import ru.innopolis.tbank.thealth.services.KeycloakAdminClient;
import ru.innopolis.tbank.thealth.services.UserService;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AchievementService achievementService;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private FoodEntryRepository foodEntryRepository;
    @Mock
    private UserAchievementRepository userAchievementRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private PostRepository postRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                achievementService,
                keycloakAdminClient,
                workoutRepository,
                foodEntryRepository,
                userAchievementRepository,
                recipeRepository,
                postRepository
        );
    }

    @Test
    @DisplayName("GET /me возвращает существующий локальный профиль")
    void getCurrentUser_whenExists_shouldReturnWithoutCreating() {
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser(TestFixtures.jwt(TestFixtures.USER_ID));

        assertThat(response.id()).isEqualTo(TestFixtures.USER_ID);
        assertThat(response.username()).isEqualTo("george");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(achievementService);
    }

    @Test
    @DisplayName("Первый GET /me создаёт профиль из JWT и выдаёт welcome-достижение")
    void getCurrentUser_whenMissing_shouldCreateFromJwt() {
        Jwt jwt = TestFixtures.jwt(TestFixtures.USER_ID);
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.getCurrentUser(jwt);

        assertThat(response.id()).isEqualTo(TestFixtures.USER_ID);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.username()).isEqualTo("testuser");
        verify(achievementService).grantAchievementIfNotExists(
                TestFixtures.USER_ID,
                "WELCOME_TO_T_HEALTH"
        );
    }

    @Test
    @DisplayName("Профиль не создаётся без email в токене")
    void getCurrentUser_withoutEmail_shouldThrowBadRequest() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(TestFixtures.USER_ID.toString())
                .claim("preferred_username", "george")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(jwt))
                .isInstanceOf(MissingTokenClaimException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Username другого пользователя нельзя занять")
    void updateCurrentUser_whenUsernameTaken_shouldThrowConflict() {
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("occupied")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateCurrentUser(
                TestFixtures.jwt(TestFixtures.USER_ID),
                new UpdateUserRequest("occupied", null, null)
        )).isInstanceOf(DuplicateUsernameException.class);

        assertThat(user.getUsername()).isEqualTo("george");
    }

    @Test
    @DisplayName("PATCH профиля изменяет только переданные поля")
    void updateCurrentUser_shouldPatchFields() {
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("new-name")).thenReturn(false);

        UserResponse response = userService.updateCurrentUser(
                TestFixtures.jwt(TestFixtures.USER_ID),
                new UpdateUserRequest("new-name", "George", null)
        );

        assertThat(response.username()).isEqualTo("new-name");
        assertThat(response.firstName()).isEqualTo("George");
        assertThat(response.lastName()).isEqualTo("User");
    }

    @Test
    @DisplayName("Удаление профиля очищает зависимые данные и удаляет пользователя в Keycloak")
    void deleteUser_shouldDeleteDependenciesAndKeycloakAccount() {
        UUID userId = TestFixtures.USER_ID;
        UserEntity user = TestFixtures.user(userId, "george");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUser(TestFixtures.jwt(userId));

        verify(postRepository).deleteAllByUser_KeycloakId(userId);
        verify(workoutRepository).deleteAllByUser_KeycloakId(userId);
        verify(foodEntryRepository).deleteAllByUser_KeycloakId(userId);
        verify(recipeRepository).deleteAllByUser_KeycloakId(userId);
        verify(userAchievementRepository).deleteAllByUser_KeycloakId(userId);
        verify(userRepository).delete(user);
        verify(keycloakAdminClient).deleteUser(userId);
    }
}
