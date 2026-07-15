package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.WorkoutType;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostControllerIT extends AbstractIntegrationTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Test
    @DisplayName("POST /api/posts/text создаёт публичный текстовый пост")
    void createTextPost_shouldReturn201AndPersist() throws Exception {
        persistUser(TestFixtures.USER_ID, "post-user");

        mockMvc.perform(post("/api/posts/text")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "post": {
                                    "postTitle": "My progress"
                                  },
                                  "content": "First week completed"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(TestFixtures.USER_ID.toString()))
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.content").value("First week completed"));

        assertThat(postRepository.findAllByUser_KeycloakIdOrderByCreatedAtDesc(TestFixtures.USER_ID))
                .singleElement()
                .satisfies(post -> {
                    assertThat(post.getTitle()).isEqualTo("My progress");
                    assertThat(post.getWorkout()).isNull();
                    assertThat(post.getRecipe()).isNull();
                });
    }

    @Test
    @DisplayName("Один workout нельзя опубликовать дважды")
    void shareWorkoutTwice_shouldReturn409OnSecondRequest() throws Exception {
        UserEntity user = persistUser(TestFixtures.USER_ID, "post-user");

        WorkoutEntity workout = new WorkoutEntity();
        workout.setUser(user);
        workout.setTitle("Morning run");
        workout.setType(WorkoutType.CARDIO);
        workout.setDurationMinutes(45);
        workout.setCaloriesBurned(350);
        workout = workoutRepository.saveAndFlush(workout);

        String body = """
                {
                  "postTitle": "Morning workout"
                }
                """;

        mockMvc.perform(post("/api/posts/workouts/{id}/share", workout.getId())
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WORKOUT"));

        mockMvc.perform(post("/api/posts/workouts/{id}/share", workout.getId())
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Workout is already published"));
    }

    @Test
    @DisplayName("Публичная лента поддерживает фильтр по типу поста")
    void feed_withTypeFilter_shouldReturnOnlyRequestedType() throws Exception {
        persistUser(TestFixtures.USER_ID, "post-user");

        mockMvc.perform(post("/api/posts/text")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "post": {"postTitle": "Text post"},
                                  "content": "Content"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts/feed")
                        .param("type", "TEXT")
                        .with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TEXT"));
    }

    @Test
    @DisplayName("Пустой контент текстового поста возвращает 400")
    void createTextPost_withBlankContent_shouldReturn400() throws Exception {
        persistUser(TestFixtures.USER_ID, "post-user");

        mockMvc.perform(post("/api/posts/text")
                        .with(jwtFor(TestFixtures.USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "post": {"postTitle": "Title"},
                                  "content": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.content").exists());
    }
}
