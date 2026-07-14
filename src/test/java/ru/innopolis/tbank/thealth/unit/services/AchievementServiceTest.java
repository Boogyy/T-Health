package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.innopolis.tbank.thealth.dto.response.AchievementResponse;
import ru.innopolis.tbank.thealth.dto.response.UserAchievementResponse;
import ru.innopolis.tbank.thealth.entities.AchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserAchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.AchievementNotFoundException;
import ru.innopolis.tbank.thealth.mappers.AchievementMapper;
import ru.innopolis.tbank.thealth.repositories.AchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.AchievementService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111");

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @Mock
    private UserRepository userRepository;

    private final AchievementMapper achievementMapper = new AchievementMapper();

    @Test
    void getAllAchievements_mapsRepositoryEntities() {
        AchievementEntity first = achievement("FIRST_FOOD_ENTRY", "Первая запись питания");
        AchievementEntity second = achievement("FIRST_WORKOUT", "Первая тренировка");
        when(achievementRepository.findAll()).thenReturn(List.of(first, second));

        AchievementService service = service();
        List<AchievementResponse> result = service.getAllAchievements();

        assertThat(result).extracting(AchievementResponse::code)
                .containsExactly("FIRST_FOOD_ENTRY", "FIRST_WORKOUT");
    }

    @Test
    void getCurrentUserAchievements_mapsUserAchievements() {
        UserEntity user = user();
        UserAchievementEntity received = new UserAchievementEntity();
        received.setId(UUID.randomUUID());
        received.setUser(user);
        received.setAchievement(achievement("FIRST_WORKOUT", "Первая тренировка"));
        received.setReceivedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        when(userAchievementRepository.findAllByUser_KeycloakIdOrderByReceivedAtDesc(USER_ID))
                .thenReturn(List.of(received));

        AchievementService service = service();
        List<UserAchievementResponse> result = service.getCurrentUserAchievements(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(USER_ID);
        assertThat(result.get(0).achievement().code()).isEqualTo("FIRST_WORKOUT");
    }

    @Test
    void grantAchievementIfNotExists_alreadyGranted_doesNothing() {
        when(userAchievementRepository
                .existsByUser_KeycloakIdAndAchievement_Code(USER_ID, "FIRST_WORKOUT"))
                .thenReturn(true);

        service().grantAchievementIfNotExists(USER_ID, "FIRST_WORKOUT");

        verify(userAchievementRepository, never()).save(any());
        verifyNoInteractions(userRepository, achievementRepository);
    }

    @Test
    void grantAchievementIfNotExists_newAchievement_savesRelation() {
        UserEntity user = user();
        AchievementEntity achievement = achievement("FIRST_WORKOUT", "Первая тренировка");

        when(userAchievementRepository
                .existsByUser_KeycloakIdAndAchievement_Code(USER_ID, "FIRST_WORKOUT"))
                .thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(achievementRepository.findByCode("FIRST_WORKOUT")).thenReturn(Optional.of(achievement));
        when(userAchievementRepository
                .existsByUser_KeycloakIdAndAchievement_Code(USER_ID, "FIVE_ACHIEVEMENTS"))
                .thenReturn(true);

        service().grantAchievementIfNotExists(USER_ID, "FIRST_WORKOUT");

        ArgumentCaptor<UserAchievementEntity> captor = ArgumentCaptor.forClass(UserAchievementEntity.class);
        verify(userAchievementRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getAchievement()).isSameAs(achievement);
    }

    @Test
    void grantAchievementIfNotExists_fifthAchievement_alsoGrantsFiveAchievementsBadge() {
        UserEntity user = user();
        AchievementEntity first = achievement("FIRST_WORKOUT", "Первая тренировка");
        AchievementEntity fifth = achievement("FIVE_ACHIEVEMENTS", "Начало положено");

        when(userAchievementRepository
                .existsByUser_KeycloakIdAndAchievement_Code(USER_ID, "FIRST_WORKOUT"))
                .thenReturn(false);
        when(userAchievementRepository
                .existsByUser_KeycloakIdAndAchievement_Code(USER_ID, "FIVE_ACHIEVEMENTS"))
                .thenReturn(false);
        when(userAchievementRepository.countByUser_KeycloakId(USER_ID)).thenReturn(5L);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(achievementRepository.findByCode("FIRST_WORKOUT")).thenReturn(Optional.of(first));
        when(achievementRepository.findByCode("FIVE_ACHIEVEMENTS")).thenReturn(Optional.of(fifth));

        service().grantAchievementIfNotExists(USER_ID, "FIRST_WORKOUT");

        verify(userAchievementRepository, times(2)).save(any(UserAchievementEntity.class));
        verify(achievementRepository).findByCode("FIVE_ACHIEVEMENTS");
    }

    @Test
    void grantAchievementIfNotExists_unknownCode_throwsNotFound() {
        when(userAchievementRepository
                .existsByUser_KeycloakIdAndAchievement_Code(USER_ID, "UNKNOWN"))
                .thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(achievementRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().grantAchievementIfNotExists(USER_ID, "UNKNOWN"))
                .isInstanceOf(AchievementNotFoundException.class);

        verify(userAchievementRepository, never()).save(any());
    }

    private AchievementService service() {
        return new AchievementService(
                achievementRepository,
                userAchievementRepository,
                userRepository,
                achievementMapper
        );
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setKeycloakId(USER_ID);
        user.setUsername("george");
        user.setEmail("george@example.com");
        return user;
    }

    private AchievementEntity achievement(String code, String title) {
        AchievementEntity entity = new AchievementEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(code);
        entity.setTitle(title);
        entity.setDescription(title);
        entity.setCreatedAt(LocalDateTime.of(2026, 7, 14, 9, 0));
        return entity;
    }
}
