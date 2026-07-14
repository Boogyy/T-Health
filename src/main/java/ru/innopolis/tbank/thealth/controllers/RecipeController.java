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
import ru.innopolis.tbank.thealth.dto.request.RecipeCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.RecipeUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.dto.response.RecipeResponse;
import ru.innopolis.tbank.thealth.services.RecipeService;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Recipes",
        description = "Рецепты текущего пользователя с ингредиентами, шагами и КБЖУ"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @Operation(summary = "Получить свои рецепты")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Рецепты получены",
                    content = @Content(
                            array = @ArraySchema(
                                    schema = @Schema(implementation = RecipeResponse.class)
                            )
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
    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getAllRecipes(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = recipeService.getAllUserRecipes(jwt);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @Operation(
            summary = "Получить рецепт по ID",
            description = "Доступен только владельцу рецепта"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Рецепт получен",
                    content = @Content(
                            schema = @Schema(implementation = RecipeResponse.class)
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
                    responseCode = "403",
                    description = "Рецепт принадлежит другому пользователю",
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
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getPreciseRecipe(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "ID рецепта", required = true)
            @PathVariable("id") UUID id
    ) {
        var result = recipeService.getRecipeById(id, jwt);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @Operation(
            summary = "Создать рецепт",
            description = "Рецепт не публикуется в ленте автоматически"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Рецепт создан",
                    content = @Content(
                            schema = @Schema(implementation = RecipeResponse.class)
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
    @PostMapping
    public ResponseEntity<RecipeResponse> createRecipe(
            @Valid @RequestBody RecipeCreateRequest recipeCreateRequest,

            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt
    ) {
        var res = recipeService.createRecipe(recipeCreateRequest, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(
            summary = "Обновить рецепт",
            description = "Частично обновляет рецепт текущего пользователя"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Рецепт обновлён",
                    content = @Content(
                            schema = @Schema(implementation = RecipeResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные или UUID",
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
                    description = "Рецепт принадлежит другому пользователю",
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
            )
    })
    @PatchMapping("/{id}")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @Parameter(description = "ID рецепта", required = true)
            @PathVariable("id") UUID id,

            @Valid @RequestBody RecipeUpdateRequest recipeUpdateRequest,

            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt
    ) {
        var res = recipeService.updateRecipe(id, jwt, recipeUpdateRequest);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @Operation(
            summary = "Удалить рецепт",
            description = """
                    Если рецепт опубликован, передайте
                    deleteRelatedPost=true для удаления связанного поста
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Рецепт удалён",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректный UUID или параметр",
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
                    description = "Рецепт принадлежит другому пользователю",
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
                    description = "Рецепт связан с постом",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "ID рецепта", required = true)
            @PathVariable UUID id,

            @Parameter(description = "Удалить связанный пост")
            @RequestParam(defaultValue = "false") boolean deleteRelatedPost
    ) {
        recipeService.deleteById(jwt, id, deleteRelatedPost);
        return ResponseEntity.noContent().build();
    }
}