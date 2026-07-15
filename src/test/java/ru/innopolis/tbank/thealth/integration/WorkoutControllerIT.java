package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.WorkoutType;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkoutControllerIT extends AbstractIntegrationTest {

    @Autowired
    private WorkoutRepository workoutRepository;

    @Test
    @DisplayName("POST /api/workouts сохраняет тренировку в PostgreSQL")
    void createWorkout_shouldReturn201AndPersist() throws Exception {
        persistUser(TestFixtures.USER_ID, "workout-user");

        mockMvc.perform(post("/api/workouts")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Morning run",
                                  "type": "CARDIO",
                                  "description": "Easy cardio",
                                  "durationMinutes": 45,
                                  "caloriesBurned": 350
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(TestFixtures.USER_ID.toString()))
                .andExpect(jsonPath("$.title").value("Morning run"))
                .andExpect(jsonPath("$.type").value("CARDIO"));

        assertThat(workoutRepository.findAllByUser_KeycloakIdOrderByWorkoutDateDesc(TestFixtures.USER_ID))
                .singleElement()
                .satisfies(workout -> {
                    assertThat(workout.getDurationMinutes()).isEqualTo(45);
                    assertThat(workout.getCaloriesBurned()).isEqualTo(350);
                });
    }

    @Test
    @DisplayName("Нулевая длительность тренировки возвращает 400, а не 500")
    void createWorkout_withInvalidDuration_shouldReturn400() throws Exception {
        persistUser(TestFixtures.USER_ID, "workout-user");

        mockMvc.perform(post("/api/workouts")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid workout",
                                  "type": "CARDIO",
                                  "durationMinutes": 0,
                                  "caloriesBurned": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.durationMinutes").exists());
    }

    @Test
    @DisplayName("Пользователь не может получить чужую тренировку")
    void getWorkout_ownedByAnotherUser_shouldReturn404() throws Exception {
        persistUser(TestFixtures.USER_ID, "requester");
        UserEntity owner = persistUser(TestFixtures.OTHER_USER_ID, "owner");

        WorkoutEntity workout = new WorkoutEntity();
        workout.setUser(owner);
        workout.setTitle("Private workout");
        workout.setType(WorkoutType.STRENGTH);
        workout.setDurationMinutes(30);
        workout = workoutRepository.saveAndFlush(workout);

        mockMvc.perform(get("/api/workouts/{id}", workout.getId())
                        .with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Workout not found by id " + workout.getId()));
    }

    @Test
    @DisplayName("Запрос тренировок без JWT возвращает 401")
    void getWorkouts_withoutAuthentication_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/workouts"))
                .andExpect(status().isUnauthorized());
    }
}
