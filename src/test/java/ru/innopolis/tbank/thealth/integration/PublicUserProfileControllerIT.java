package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.entities.CommunityMemberEntity;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.CommunityRole;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.enums.WorkoutType;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityRepository;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.RecipeRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicUserProfileControllerIT extends AbstractIntegrationTest {

    private static final UUID COMMUNITY_OWNER_ID =
            UUID.fromString("33333333-aaaa-bbbb-cccc-333333333333");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Test
    @DisplayName("Публичный профиль возвращает данные пользователя и не раскрывает email")
    void getPublicProfile_shouldReturnPublicDataWithoutEmail() throws Exception {
        persistUser(TestFixtures.OTHER_USER_ID, "DoubleCheck");

        mockMvc.perform(get(
                        "/api/users/public/{username}",
                        "DoubleCheck"
                ).with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId")
                        .value(TestFixtures.OTHER_USER_ID.toString()))
                .andExpect(jsonPath("$.username")
                        .value("DoubleCheck"))
                .andExpect(jsonPath("$.firstName")
                        .value("Test"))
                .andExpect(jsonPath("$.lastName")
                        .value("User"))
                .andExpect(jsonPath("$.memberSince").exists())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.publications").isArray())
                .andExpect(jsonPath("$.communities").isArray());
    }

    @Test
    @DisplayName("Username публичного профиля учитывает регистр")
    void getPublicProfile_withDifferentCase_shouldReturn404() throws Exception {
        persistUser(TestFixtures.OTHER_USER_ID, "DoubleCheck");

        mockMvc.perform(get(
                        "/api/users/public/{username}",
                        "doublecheck"
                ).with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Публичный профиль возвращает только PUBLIC-публикации")
    void getPublicProfile_shouldReturnOnlyPublicPosts() throws Exception {
        UserEntity profileUser =
                persistUser(TestFixtures.OTHER_USER_ID, "DoubleCheck");

        CommunityEntity community =
                saveCommunity(profileUser, "Закрытое обсуждение");

        PostEntity publicPost = saveTextPost(
                profileUser,
                PostVisibility.PUBLIC,
                null,
                "Публичная публикация"
        );

        saveTextPost(
                profileUser,
                PostVisibility.COMMUNITY,
                community,
                "Публикация сообщества"
        );

        saveUnpublishedWorkout(profileUser);
        saveUnpublishedRecipe(profileUser);

        mockMvc.perform(get(
                        "/api/users/public/{username}",
                        "DoubleCheck"
                ).with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publications.length()")
                        .value(1))
                .andExpect(jsonPath("$.publications[0].id")
                        .value(publicPost.getId().toString()))
                .andExpect(jsonPath("$.publications[0].title")
                        .value("Публичная публикация"))
                .andExpect(jsonPath("$.publications[0].visibility")
                        .value("PUBLIC"))
                .andExpect(jsonPath("$.publications[0].type")
                        .value("TEXT"));
    }

    @Test
    @DisplayName("Публичный профиль возвращает сообщества, роль и дату вступления")
    void getPublicProfile_shouldReturnCommunitiesWithMembershipData()
            throws Exception {
        UserEntity owner =
                persistUser(COMMUNITY_OWNER_ID, "community-owner");
        UserEntity profileUser =
                persistUser(TestFixtures.OTHER_USER_ID, "DoubleCheck");

        CommunityEntity community =
                saveCommunity(owner, "Бег по утрам");

        LocalDateTime joinedAt =
                LocalDateTime.of(2026, 8, 1, 9, 30);

        CommunityMemberEntity membership =
                new CommunityMemberEntity();
        membership.setCommunity(community);
        membership.setUser(profileUser);
        membership.setRole(CommunityRole.MEMBER);
        membership.setJoinedAt(joinedAt);
        communityMemberRepository.saveAndFlush(membership);

        mockMvc.perform(get(
                        "/api/users/public/{username}",
                        "DoubleCheck"
                ).with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communities.length()")
                        .value(1))
                .andExpect(jsonPath("$.communities[0].id")
                        .value(community.getId().toString()))
                .andExpect(jsonPath("$.communities[0].communityName")
                        .value("Бег по утрам"))
                .andExpect(jsonPath("$.communities[0].description")
                        .value("Тестовое сообщество"))
                .andExpect(jsonPath("$.communities[0].role")
                        .value("MEMBER"))
                .andExpect(jsonPath("$.communities[0].joinedAt")
                        .exists());
    }

    @Test
    @DisplayName("Неизвестный username возвращает 404")
    void getPublicProfile_unknownUsername_shouldReturn404() throws Exception {
        mockMvc.perform(get(
                        "/api/users/public/{username}",
                        "unknown-user"
                ).with(jwtFor(TestFixtures.USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Публичный профиль без JWT недоступен")
    void getPublicProfile_withoutAuthentication_shouldReturn401()
            throws Exception {
        mockMvc.perform(get(
                        "/api/users/public/{username}",
                        "DoubleCheck"
                ))
                .andExpect(status().isUnauthorized());
    }

    private CommunityEntity saveCommunity(
            UserEntity owner,
            String name
    ) {
        CommunityEntity community = new CommunityEntity();
        community.setOwner(owner);
        community.setCommunityName(name);
        community.setDescription("Тестовое сообщество");

        CommunityEntity savedCommunity =
                communityRepository.saveAndFlush(community);

        CommunityMemberEntity ownerMembership =
                new CommunityMemberEntity();
        ownerMembership.setCommunity(savedCommunity);
        ownerMembership.setUser(owner);
        ownerMembership.setRole(CommunityRole.OWNER);
        communityMemberRepository.saveAndFlush(ownerMembership);

        return savedCommunity;
    }

    private PostEntity saveTextPost(
            UserEntity author,
            PostVisibility visibility,
            CommunityEntity community,
            String title
    ) {
        PostEntity post = new PostEntity();
        post.setUser(author);
        post.setCommunity(community);
        post.setVisibility(visibility);
        post.setPostType(PostType.TEXT);
        post.setTitle(title);
        post.setContent("Содержимое: " + title);
        return postRepository.saveAndFlush(post);
    }

    private WorkoutEntity saveUnpublishedWorkout(
            UserEntity user
    ) {
        WorkoutEntity workout = new WorkoutEntity();
        workout.setUser(user);
        workout.setTitle("Личная тренировка");
        workout.setType(WorkoutType.CARDIO);
        workout.setDescription("Не опубликована");
        workout.setDurationMinutes(30);
        workout.setCaloriesBurned(250);
        return workoutRepository.saveAndFlush(workout);
    }

    private RecipeEntity saveUnpublishedRecipe(
            UserEntity user
    ) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setUser(user);
        recipe.setTitle("Личный рецепт");
        recipe.setDescription("Не опубликован");
        recipe.setIngredients("Овсянка, молоко");
        recipe.setCookingSteps("Смешать и приготовить");
        recipe.setCalories(300);
        recipe.setProteins(new BigDecimal("10.00"));
        recipe.setFats(new BigDecimal("7.00"));
        recipe.setCarbohydrates(new BigDecimal("45.00"));
        return recipeRepository.saveAndFlush(recipe);
    }
}