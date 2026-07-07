package ru.innopolis.tbank.thealth.controllers;

import io.swagger.v3.oas.annotations.Operation;
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
import ru.innopolis.tbank.thealth.dto.request.CommentCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.CommentResponse;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.services.CommentService;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Comments",
        description = "Операции с комментариями под постами"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "Получить комментарии под постом")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Комментарии получены"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пост не найден или пользователь не состоит в сообществе",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getPostComments(
            @PathVariable("postId") UUID postId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = commentService.getPostComments(postId, userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Создать комментарий под постом")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Комментарий создан"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные запроса",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пост не найден или пользователь не состоит в сообществе",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable("postId") UUID postId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = commentService.createComment(postId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Удалить комментарий")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Комментарий удалён",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Недостаточно прав для удаления комментария",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пост, комментарий или участник сообщества не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable("postId") UUID postId,
            @PathVariable("commentId") UUID commentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        commentService.deleteComment(postId, commentId, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}