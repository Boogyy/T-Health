package ru.innopolis.tbank.thealth.support;

import org.springframework.security.oauth2.jwt.Jwt;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;
import ru.innopolis.tbank.thealth.enums.WorkoutType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class TestFixtures {

    public static final UUID USER_ID = UUID.fromString("11111111-aaaa-bbbb-cccc-111111111111");
    public static final UUID OTHER_USER_ID = UUID.fromString("22222222-aaaa-bbbb-cccc-222222222222");

    private TestFixtures() {
    }

    public static UserEntity user(UUID id, String username) {
        return new UserEntity(
                id,
                username,
                username + "@example.com",
                "Test",
                "User",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    public static WorkoutEntity workout(UUID id, UserEntity user) {
        return new WorkoutEntity(
                id,
                user,
                "Morning run",
                WorkoutType.CARDIO,
                "Easy cardio",
                45,
                350,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    public static RecipeEntity recipe(UUID id, UserEntity user) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(id);
        recipe.setUser(user);
        recipe.setTitle("Oatmeal");
        recipe.setDescription("Healthy breakfast");
        recipe.setIngredients("Oats, milk, berries");
        recipe.setCookingSteps("Mix and cook");
        recipe.setCalories(350);
        recipe.setProteins(new BigDecimal("12.50"));
        recipe.setFats(new BigDecimal("8.00"));
        recipe.setCarbohydrates(new BigDecimal("55.00"));
        recipe.setImageUrl("https://example.com/oatmeal.jpg");
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());
        return recipe;
    }

    public static PostEntity textPost(UUID id, UserEntity user) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setUser(user);
        post.setPostType(PostType.TEXT);
        post.setVisibility(PostVisibility.PUBLIC);
        post.setTitle("My day");
        post.setContent("Today was a good day");
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return post;
    }

    public static Jwt jwt(UUID subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject.toString())
                .claim("email", "test@example.com")
                .claim("preferred_username", "testuser")
                .claim("given_name", "Test")
                .claim("family_name", "User")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}
