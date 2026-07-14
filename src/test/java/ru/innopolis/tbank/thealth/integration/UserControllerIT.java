package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractIntegrationTest {

    @Autowired
    private UserAchievementRepository userAchievementRepository;

    @Test
    @DisplayName("Первый GET /api/users/me создаёт локальный профиль из JWT")
    void getCurrentUser_firstCall_shouldCreateProfileAndWelcomeAchievement() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TestFixtures.USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        assertThat(userRepository.findById(TestFixtures.USER_ID)).isPresent();
        assertThat(userAchievementRepository.existsByUser_KeycloakIdAndAchievement_Code(
                TestFixtures.USER_ID,
                "WELCOME_TO_T_HEALTH"
        )).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/users/me обновляет профиль")
    void updateCurrentUser_shouldReturnUpdatedProfile() throws Exception {
        persistUser(TestFixtures.USER_ID, "old-name");

        mockMvc.perform(patch("/api/users/me")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new-name",
                                  "firstName": "George",
                                  "lastName": "Tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new-name"))
                .andExpect(jsonPath("$.firstName").value("George"))
                .andExpect(jsonPath("$.lastName").value("Tester"));
    }

    @Test
    @DisplayName("Занятый username возвращает 409")
    void updateCurrentUser_withDuplicateUsername_shouldReturn409() throws Exception {
        persistUser(TestFixtures.USER_ID, "current-user");
        persistUser(TestFixtures.OTHER_USER_ID, "occupied");

        mockMvc.perform(patch("/api/users/me")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "occupied"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists: occupied"));
    }

    @Test
    @DisplayName("DELETE /api/users/me удаляет локальный профиль и вызывает Keycloak Admin Client")
    void deleteCurrentUser_shouldDeleteProfileAndKeycloakUser() throws Exception {
        persistUser(TestFixtures.USER_ID, "delete-user");

        mockMvc.perform(delete("/api/users/me")
                        .with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(TestFixtures.USER_ID)).isEmpty();
        verify(keycloakAdminClient).deleteUser(TestFixtures.USER_ID);
    }

    @Test
    @DisplayName("Профиль без JWT недоступен")
    void getCurrentUser_withoutAuthentication_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
