package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.innopolis.tbank.thealth.dto.request.RecipeCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.RecipeUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.RecipeResponse;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.RecipeEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.ConflictException;
import ru.innopolis.tbank.thealth.exceptions.RecipeNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.RecipeMapper;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.RecipeRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.RecipeService;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private PostRepository postRepository;

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(
                userRepository,
                recipeRepository,
                new RecipeMapper(),
                postRepository
        );
    }

    @Test
    @DisplayName("Создание рецепта сохраняет ингредиенты, шаги и КБЖУ")
    void createRecipe_shouldMapAndSaveAllFields() {
        UUID userId = TestFixtures.USER_ID;
        UserEntity user = TestFixtures.user(userId, "george");
        Jwt jwt = TestFixtures.jwt(userId);
        RecipeCreateRequest request = new RecipeCreateRequest(
                "Oatmeal",
                "Healthy breakfast",
                "Oats, milk, berries",
                "Mix and cook",
                350,
                new BigDecimal("12.50"),
                new BigDecimal("8.00"),
                new BigDecimal("55.00"),
                "https://example.com/oatmeal.jpg"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> {
            RecipeEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            return entity;
        });

        RecipeResponse response = recipeService.createRecipe(request, jwt);

        ArgumentCaptor<RecipeEntity> captor = ArgumentCaptor.forClass(RecipeEntity.class);
        verify(recipeRepository).save(captor.capture());
        RecipeEntity saved = captor.getValue();

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getIngredients()).isEqualTo("Oats, milk, berries");
        assertThat(saved.getCookingSteps()).isEqualTo("Mix and cook");
        assertThat(response.authorId()).isEqualTo(userId);
        assertThat(response.proteins()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("Рецепт нельзя создать для отсутствующего пользователя")
    void createRecipe_whenUserMissing_shouldThrow() {
        UUID userId = TestFixtures.USER_ID;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        RecipeCreateRequest request = new RecipeCreateRequest(
                "Recipe", "Description", "Ingredients", "Steps",
                null, null, null, null, null
        );

        assertThatThrownBy(() -> recipeService.createRecipe(request, TestFixtures.jwt(userId)))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(recipeRepository);
    }

    @Test
    @DisplayName("PATCH рецепта сохраняет старые значения для null-полей")
    void updateRecipe_shouldChangeOnlyProvidedFields() {
        UUID recipeId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        RecipeEntity recipe = TestFixtures.recipe(recipeId, user);
        String oldIngredients = recipe.getIngredients();

        when(recipeRepository.findByIdAndUser_KeycloakId(recipeId, user.getKeycloakId()))
                .thenReturn(Optional.of(recipe));

        RecipeUpdateRequest request = new RecipeUpdateRequest(
                "Updated oatmeal",
                null,
                null,
                "Mix, cook and serve",
                400,
                null,
                null,
                null,
                null
        );

        RecipeResponse response = recipeService.updateRecipe(recipeId, TestFixtures.jwt(user.getKeycloakId()), request);

        assertThat(recipe.getTitle()).isEqualTo("Updated oatmeal");
        assertThat(recipe.getIngredients()).isEqualTo(oldIngredients);
        assertThat(recipe.getCookingSteps()).isEqualTo("Mix, cook and serve");
        assertThat(response.calories()).isEqualTo(400);
        verify(recipeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Опубликованный рецепт нельзя удалить без подтверждения")
    void deleteRecipe_whenPublishedAndNotConfirmed_shouldThrowConflict() {
        UUID recipeId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        RecipeEntity recipe = TestFixtures.recipe(recipeId, user);

        when(recipeRepository.findByIdAndUser_KeycloakId(recipeId, user.getKeycloakId()))
                .thenReturn(Optional.of(recipe));
        when(postRepository.findByRecipe_Id(recipeId)).thenReturn(Optional.of(new PostEntity()));

        assertThatThrownBy(() -> recipeService.deleteById(TestFixtures.jwt(user.getKeycloakId()), recipeId, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("published");

        verify(recipeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("При подтверждении удаляются связанный пост и рецепт")
    void deleteRecipe_whenConfirmed_shouldDeleteDependencies() {
        UUID recipeId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(TestFixtures.USER_ID, "george");
        RecipeEntity recipe = TestFixtures.recipe(recipeId, user);
        PostEntity post = new PostEntity();

        when(recipeRepository.findByIdAndUser_KeycloakId(recipeId, user.getKeycloakId()))
                .thenReturn(Optional.of(recipe));
        when(postRepository.findByRecipe_Id(recipeId)).thenReturn(Optional.of(post));

        recipeService.deleteById(TestFixtures.jwt(user.getKeycloakId()), recipeId, true);

        verify(postRepository).delete(post);
        verify(recipeRepository).delete(recipe);
    }

    @Test
    @DisplayName("Чужой рецепт не возвращается пользователю")
    void getRecipe_whenNotOwned_shouldThrowNotFound() {
        UUID recipeId = UUID.randomUUID();
        when(recipeRepository.findByIdAndUser_KeycloakId(recipeId, TestFixtures.USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.getRecipeById(recipeId, TestFixtures.jwt(TestFixtures.USER_ID)))
                .isInstanceOf(RecipeNotFoundException.class);
    }
}
