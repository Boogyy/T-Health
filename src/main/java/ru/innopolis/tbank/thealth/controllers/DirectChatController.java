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
import ru.innopolis.tbank.thealth.dto.request.DirectChatCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.DirectMessageCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.DirectChatResponse;
import ru.innopolis.tbank.thealth.dto.response.DirectMessageResponse;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.services.DirectChatService;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Direct Chats",
        description = "Операции с личными чатами 1 на 1 между пользователями"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/direct-chats")
public class DirectChatController {

    private final DirectChatService directChatService;

    public DirectChatController(DirectChatService directChatService) {
        this.directChatService = directChatService;
    }

    @Operation(summary = "Получить личные чаты текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список личных чатов получен"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<DirectChatResponse>> getCurrentUserChats(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = directChatService.getCurrentUserChats(userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Создать или получить существующий личный чат")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Личный чат уже существует"),
            @ApiResponse(responseCode = "201", description = "Личный чат создан"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные данные запроса или попытка создать чат с самим собой",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Получатель не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<DirectChatResponse> createDirectChat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DirectChatCreateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = directChatService.createDirectChat(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Получить сообщения личного чата")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сообщения личного чата получены"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Личный чат не найден или пользователь не является участником",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<DirectMessageResponse>> getChatMessages(
            @PathVariable("id") UUID chatId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = directChatService.getChatMessages(chatId, userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Отправить сообщение в личный чат")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщение отправлено"),
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
                    description = "Личный чат не найден или пользователь не является участником",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{id}/messages")
    public ResponseEntity<DirectMessageResponse> createMessage(
            @PathVariable("id") UUID chatId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DirectMessageCreateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = directChatService.createMessage(chatId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}