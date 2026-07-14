package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.innopolis.tbank.thealth.entities.AchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserAchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.repositories.AchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AchievementControllerIT extends AbstractIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Autowired
    private AchievementRepository achievementRepository;

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    @Test
    @DisplayName("GET /api/achievements возвращает достижения, созданные миграцией")
    void getAllAchievements_authenticatedUser_returnsSeededAchievements() throws Exception {
        persistUser(USER_ID, "achievement-user");

        mockMvc.perform(get("/api/achievements")
                        .with(jwtFor(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.code == 'WELCOME_TO_T_HEALTH')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'FIRST_WORKOUT')]").exists());
    }

    @Test
    @DisplayName("GET /api/users/me/achievements возвращает только достижения текущего пользователя")
    void getCurrentUserAchievements_returnsOnlyCurrentUserRecords() throws Exception {
        UserEntity current = persistUser(USER_ID, "achievement-user");
        UserEntity other = persistUser(OTHER_USER_ID, "other-achievement-user");
        AchievementEntity firstWorkout = achievementRepository.findByCode("FIRST_WORKOUT").orElseThrow();
        AchievementEntity firstFood = achievementRepository.findByCode("FIRST_FOOD_ENTRY").orElseThrow();
        saveUserAchievement(current, firstWorkout);
        saveUserAchievement(other, firstFood);

        mockMvc.perform(get("/api/users/me/achievements")
                        .with(jwtFor(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$[0].achievement.code").value("FIRST_WORKOUT"));
    }

    @Test
    @DisplayName("Достижения без JWT недоступны")
    void getAchievements_withoutJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/achievements"))
                .andExpect(status().isUnauthorized());
    }

    private UserAchievementEntity saveUserAchievement(UserEntity user, AchievementEntity achievement) {
        UserAchievementEntity entity = new UserAchievementEntity();
        entity.setUser(user);
        entity.setAchievement(achievement);
        return userAchievementRepository.saveAndFlush(entity);
    }
}
