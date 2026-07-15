package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.entities.FoodEntryEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.repositories.FoodEntryRepository;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FoodEntryControllerIT extends AbstractIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    @Test
    @DisplayName("POST /api/food-entries сохраняет запись и выдаёт первое достижение питания")
    void createFoodEntry_validRequest_returnsCreatedAndGrantsAchievement() throws Exception {
        persistUser(USER_ID, "food-user");

        mockMvc.perform(post("/api/food-entries")
                        .with(jwtFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealName": "Овсянка с ягодами",
                                  "calories": 350,
                                  "proteins": 12.50,
                                  "fats": 8.00,
                                  "carbohydrates": 55.00,
                                  "mealDate": "2026-07-14T09:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealName").value("Овсянка с ягодами"))
                .andExpect(jsonPath("$.calories").value(350))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));

        assertThat(foodEntryRepository.countByUser_KeycloakId(USER_ID)).isEqualTo(1);
        assertThat(userAchievementRepository
                .existsByUser_KeycloakIdAndAchievement_Code(USER_ID, "FIRST_FOOD_ENTRY"))
                .isTrue();
    }

    @Test
    @DisplayName("Отрицательные калории возвращают 400, а не 500")
    void createFoodEntry_negativeCalories_returnsBadRequestNot500() throws Exception {
        persistUser(USER_ID, "food-user");

        mockMvc.perform(post("/api/food-entries")
                        .with(jwtFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealName": "Некорректная запись",
                                  "calories": -1,
                                  "proteins": 10,
                                  "fats": 5,
                                  "carbohydrates": 20
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.calories").exists());
    }

    @Test
    @DisplayName("GET /api/food-entries/daily рассчитывает сумму КБЖУ за выбранный день")
    void getDailyFoodEntries_twoMeals_returnsCalculatedTotals() throws Exception {
        UserEntity user = persistUser(USER_ID, "food-user");
        LocalDate date = LocalDate.of(2026, 7, 14);
        saveEntry(user, "Завтрак", 350, "12.50", "8.00", "55.00", date.atTime(9, 0));
        saveEntry(user, "Ужин", 650, "30.00", "20.00", "70.00", date.atTime(19, 0));

        mockMvc.perform(get("/api/food-entries/daily")
                        .param("date", date.toString())
                        .with(jwtFor(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-07-14"))
                .andExpect(jsonPath("$.totalCalories").value(1000))
                .andExpect(jsonPath("$.totalProteins").value(42.5))
                .andExpect(jsonPath("$.totalFats").value(28.0))
                .andExpect(jsonPath("$.totalCarbohydrates").value(125.0))
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    @DisplayName("Пользователь не может получить чужую запись питания")
    void getFoodEntry_foreignOwner_returnsNotFound() throws Exception {
        UserEntity owner = persistUser(OTHER_USER_ID, "food-owner");
        persistUser(USER_ID, "food-stranger");
        FoodEntryEntity entry = saveEntry(
                owner,
                "Обед",
                500,
                "25.00",
                "15.00",
                "60.00",
                LocalDateTime.now()
        );

        mockMvc.perform(get("/api/food-entries/{id}", entry.getId())
                        .with(jwtFor(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Дневник питания без JWT недоступен")
    void getFoodEntries_withoutJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/food-entries"))
                .andExpect(status().isUnauthorized());
    }

    private FoodEntryEntity saveEntry(
            UserEntity user,
            String name,
            int calories,
            String proteins,
            String fats,
            String carbohydrates,
            LocalDateTime mealDate
    ) {
        FoodEntryEntity entry = new FoodEntryEntity();
        entry.setUser(user);
        entry.setMealName(name);
        entry.setCalories(calories);
        entry.setProteins(new BigDecimal(proteins));
        entry.setFats(new BigDecimal(fats));
        entry.setCarbohydrates(new BigDecimal(carbohydrates));
        entry.setMealDate(mealDate);
        return foodEntryRepository.saveAndFlush(entry);
    }
}
