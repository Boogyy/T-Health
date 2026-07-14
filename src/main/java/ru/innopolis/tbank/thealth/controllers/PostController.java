package ru.innopolis.tbank.thealth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.*;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.services.PostService;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Posts",
        description = "Публичные посты, тренировки, рецепты и достижения в ленте"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(
            summary = "Получить публичную ленту",
            description = "Параметр type фильтрует посты по типу"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Лента получена",
                    content = @Content(
                            array = @ArraySchema(
                                    schema = @Schema(implementation = PostResponse.class)
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректный тип поста",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/feed")
    public ResponseEntity<List<PostResponse>> getAllPosts(
            @Parameter(description = "Тип поста: TEXT, WORKOUT, RECIPE или ACHIEVEMENT")
            @RequestParam(required = false) PostType type
    ) {
        var res = postService.getPosts(type);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @Operation(
            summary = "Получить свои посты",
            description = "Возвращает посты текущего пользователя с фильтрацией по типу"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Посты получены",
                    content = @Content(
                            array = @ArraySchema(
                                    schema = @Schema(implementation = PostResponse.class)
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректный тип поста",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/me")
    public ResponseEntity<List<PostResponse>> getUserPosts(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "Тип поста")
            @RequestParam(required = false) PostType type
    ) {
        var res = postService.getUserPosts(jwt, type);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @Operation(summary = "Получить пост по ID")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Пост получен",
                    content = @Content(
                            schema = @Schema(implementation = PostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректный UUID",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пост не найден",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostById(
            @Parameter(description = "ID поста", required = true)
            @PathVariable("id") UUID id
    ) {
        PostResponse response = postService.getPostById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Опубликовать достижение",
            description = "Достижение должно принадлежать текущему пользователю"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Достижение опубликовано",
                    content = @Content(
                            schema = @Schema(implementation = PostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Нет доступа к достижению",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Достижение не найдено",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Достижение уже опубликовано",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/achievements/{id}/share")
    public ResponseEntity<PostResponse> postAchievement(
            @Parameter(description = "ID полученного достижения", required = true)
            @PathVariable("id") UUID id,

            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody PostInfoRequest request
    ) {
        var res = postService.postAchievement(id, jwt, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(
            summary = "Опубликовать рецепт",
            description = "Создаёт пост для существующего рецепта пользователя"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Рецепт опубликован",
                    content = @Content(
                            schema = @Schema(implementation = PostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Нет доступа к рецепту",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Рецепт не найден",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Рецепт уже опубликован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/recipes/{id}/share")
    public ResponseEntity<PostResponse> postRecipe(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "ID рецепта", required = true)
            @PathVariable("id") UUID id,

            @Valid @RequestBody PostInfoRequest request
    ) {
        var res = postService.postRecipe(id, jwt, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(
            summary = "Опубликовать тренировку",
            description = "Создаёт пост для существующей тренировки пользователя"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Тренировка опубликована",
                    content = @Content(
                            schema = @Schema(implementation = PostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Нет доступа к тренировке",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Тренировка не найдена",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Тренировка уже опубликована",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/workouts/{id}/share")
    public ResponseEntity<PostResponse> postWorkout(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "ID тренировки", required = true)
            @PathVariable("id") UUID id,

            @Valid @RequestBody PostInfoRequest request
    ) {
        var res = postService.postWorkout(id, jwt, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(
            summary = "Создать и опубликовать тренировку",
            description = "Создаёт тренировку и связанный пост"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Тренировка и пост созданы",
                    content = @Content(
                            schema = @Schema(implementation = PostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/workouts")
    public ResponseEntity<PostResponse> createWorkoutPost(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody WorkoutPostCreateRequest workoutPostCreateRequest
    ) {
        var res = postService.createWorkoutEntryPost(jwt, workoutPostCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(
            summary = "Создать и опубликовать рецепт",
            description = "Создаёт рецепт и связанный пост"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Рецепт и пост созданы",
                    content = @Content(
                            schema = @Schema(implementation = PostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/recipes")
    public ResponseEntity<PostResponse> createRecipePost(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody RecipePostCreateRequest recipePostCreateRequest
    ) {
        var res = postService.createRecipePost(jwt, recipePostCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Создать текстовый пост")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Пост создан",
                    content = @Content(
                            schema = @Schema(implementation = PostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/text")
    public ResponseEntity<PostResponse> createTextPost(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody TextPostCreateRequest textPostCreateRequest
    ) {
        var res = postService.createTextPost(jwt, textPostCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(
            summary = "Удалить свой пост",
            description = "Связанная тренировка, рецепт или достижение не удаляются"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Пост удалён",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректный UUID",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Пост принадлежит другому пользователю",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пост не найден",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "ID поста", required = true)
            @PathVariable("id") UUID postId
    ) {
        postService.deletePostById(jwt, postId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}