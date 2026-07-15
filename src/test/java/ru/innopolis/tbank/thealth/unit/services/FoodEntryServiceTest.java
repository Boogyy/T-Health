package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.DailyFoodEntriesResponse;
import ru.innopolis.tbank.thealth.entities.FoodEntryEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.FoodEntryNotFoundException;
import ru.innopolis.tbank.thealth.repositories.FoodEntryRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.AchievementService;
import ru.innopolis.tbank.thealth.services.FoodEntryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodEntryServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111");
    private static final UUID ENTRY_ID = UUID.fromString("22222222-bbbb-bbbb-bbbb-222222222222");
    private static final LocalDateTime MEAL_DATE = LocalDateTime.of(2026, 7, 14, 9, 30);

    @Mock
    private FoodEntryRepository foodEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private FoodEntryService foodEntryService;

    @Test
    void createFoodEntry_validRequest_savesEntryAndGrantsFirstAchievement() {
        UserEntity user = user(USER_ID, "george");
        FoodEntryCreateRequest request = new FoodEntryCreateRequest(
                "Овсянка",
                350,
                new BigDecimal("12.50"),
                new BigDecimal("8.00"),
                new BigDecimal("55.00"),
                MEAL_DATE
        );

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(foodEntryRepository.save(any(FoodEntryEntity.class))).thenAnswer(invocation -> {
            FoodEntryEntity saved = invocation.getArgument(0);
            saved.setId(ENTRY_ID);
            saved.setCreatedAt(MEAL_DATE.plusMinutes(1));
            saved.setUpdatedAt(MEAL_DATE.plusMinutes(1));
            return saved;
        });
        when(foodEntryRepository.countByUser_KeycloakId(USER_ID)).thenReturn(1L);

        var response = foodEntryService.createFoodEntry(request, USER_ID);

        ArgumentCaptor<FoodEntryEntity> captor = ArgumentCaptor.forClass(FoodEntryEntity.class);
        verify(foodEntryRepository).save(captor.capture());
        FoodEntryEntity saved = captor.getValue();

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getMealName()).isEqualTo("Овсянка");
        assertThat(saved.getCalories()).isEqualTo(350);
        assertThat(saved.getMealDate()).isEqualTo(MEAL_DATE);
        assertThat(response.id()).isEqualTo(ENTRY_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);

        verify(achievementService).grantAchievementIfNotExists(USER_ID, "FIRST_FOOD_ENTRY");
        verify(achievementService, never()).grantAchievementIfNotExists(USER_ID, "TEN_FOOD_ENTRIES");
    }

    @Test
    void createFoodEntry_tenthEntry_grantsBothFoodAchievements() {
        UserEntity user = user(USER_ID, "george");
        FoodEntryCreateRequest request = createRequest();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(foodEntryRepository.save(any(FoodEntryEntity.class))).thenAnswer(invocation -> {
            FoodEntryEntity saved = invocation.getArgument(0);
            saved.setId(ENTRY_ID);
            return saved;
        });
        when(foodEntryRepository.countByUser_KeycloakId(USER_ID)).thenReturn(10L);

        foodEntryService.createFoodEntry(request, USER_ID);

        verify(achievementService).grantAchievementIfNotExists(USER_ID, "FIRST_FOOD_ENTRY");
        verify(achievementService).grantAchievementIfNotExists(USER_ID, "TEN_FOOD_ENTRIES");
    }

    @Test
    void updateFoodEntry_partialRequest_changesOnlyProvidedFields() {
        FoodEntryEntity entry = entry(ENTRY_ID, user(USER_ID, "george"), "Старое блюдо", 100, MEAL_DATE);
        FoodEntryUpdateRequest request = new FoodEntryUpdateRequest(
                "Новое блюдо",
                450,
                null,
                null,
                null,
                null
        );

        when(foodEntryRepository.findByIdAndUser_KeycloakId(ENTRY_ID, USER_ID))
                .thenReturn(Optional.of(entry));

        var response = foodEntryService.updateFoodEntry(ENTRY_ID, USER_ID, request);

        assertThat(response.mealName()).isEqualTo("Новое блюдо");
        assertThat(response.calories()).isEqualTo(450);
        assertThat(response.proteins()).isEqualByComparingTo("10.00");
        assertThat(response.mealDate()).isEqualTo(MEAL_DATE);
        verify(foodEntryRepository, never()).save(any());
    }

    @Test
    void getDailyFoodEntries_twoEntries_calculatesTotalsForSelectedDate() {
        LocalDate selectedDate = LocalDate.of(2026, 7, 14);
        UserEntity user = user(USER_ID, "george");
        FoodEntryEntity breakfast = entry(
                UUID.randomUUID(), user, "Завтрак", 350, selectedDate.atTime(9, 0));
        FoodEntryEntity dinner = entry(
                UUID.randomUUID(), user, "Ужин", 650, selectedDate.atTime(19, 0));
        dinner.setProteins(new BigDecimal("30.00"));
        dinner.setFats(new BigDecimal("20.00"));
        dinner.setCarbohydrates(new BigDecimal("70.00"));

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(USER_ID.toString());
        when(foodEntryRepository
                .findAllByUser_KeycloakIdAndMealDateGreaterThanEqualAndMealDateLessThanOrderByMealDateAsc(
                        USER_ID,
                        selectedDate.atStartOfDay(),
                        selectedDate.plusDays(1).atStartOfDay()
                ))
                .thenReturn(List.of(breakfast, dinner));

        DailyFoodEntriesResponse response = foodEntryService.getDailyFoodEntries(jwt, selectedDate);

        assertThat(response.date()).isEqualTo(selectedDate);
        assertThat(response.totalCalories()).isEqualTo(1000);
        assertThat(response.totalProteins()).isEqualByComparingTo("40.00");
        assertThat(response.totalFats()).isEqualByComparingTo("25.00");
        assertThat(response.totalCarbohydrates()).isEqualByComparingTo("100.00");
        assertThat(response.entries()).hasSize(2);
    }

    @Test
    void getFoodEntry_foreignOrMissingEntry_throwsNotFound() {
        when(foodEntryRepository.findByIdAndUser_KeycloakId(ENTRY_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> foodEntryService.getFoodEntry(ENTRY_ID, USER_ID))
                .isInstanceOf(FoodEntryNotFoundException.class);
    }

    @Test
    void deleteFoodEntry_ownedEntry_deletesEntity() {
        FoodEntryEntity entry = entry(ENTRY_ID, user(USER_ID, "george"), "Обед", 500, MEAL_DATE);
        when(foodEntryRepository.findByIdAndUser_KeycloakId(ENTRY_ID, USER_ID))
                .thenReturn(Optional.of(entry));

        foodEntryService.deleteFoodEntry(ENTRY_ID, USER_ID);

        verify(foodEntryRepository).delete(entry);
    }

    private FoodEntryCreateRequest createRequest() {
        return new FoodEntryCreateRequest(
                "Овсянка",
                350,
                new BigDecimal("12.50"),
                new BigDecimal("8.00"),
                new BigDecimal("55.00"),
                MEAL_DATE
        );
    }

    private UserEntity user(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setKeycloakId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }

    private FoodEntryEntity entry(
            UUID id,
            UserEntity user,
            String name,
            int calories,
            LocalDateTime mealDate
    ) {
        FoodEntryEntity entry = new FoodEntryEntity();
        entry.setId(id);
        entry.setUser(user);
        entry.setMealName(name);
        entry.setCalories(calories);
        entry.setProteins(new BigDecimal("10.00"));
        entry.setFats(new BigDecimal("5.00"));
        entry.setCarbohydrates(new BigDecimal("30.00"));
        entry.setMealDate(mealDate);
        entry.setCreatedAt(mealDate);
        entry.setUpdatedAt(mealDate);
        return entry;
    }
}
