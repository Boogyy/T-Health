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
import ru.innopolis.tbank.thealth.dto.request.CommunityCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.CommunityPostCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.CommunityUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.CommunityMemberResponse;
import ru.innopolis.tbank.thealth.dto.response.CommunityResponse;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.dto.response.PostResponse;
import ru.innopolis.tbank.thealth.services.CommunityService;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Communities",
        description = "Операции с сообществами, участниками и постами внутри сообществ"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Operation(summary = "Получить список всех сообществ")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список сообществ получен"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<CommunityResponse>> getAllCommunities(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = communityService.getAllCommunities(userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Получить сообщества текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список сообществ пользователя получен"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/me")
    public ResponseEntity<List<CommunityResponse>> getCurrentUserCommunities(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = communityService.getCurrentUserCommunities(userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Получить сообщество по id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сообщество получено"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommunityResponse> getCommunity(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = communityService.getCommunity(communityId, userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Создать сообщество")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Сообщество создано"),
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
                    responseCode = "409",
                    description = "Сообщество с таким названием уже существует",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<CommunityResponse> createCommunity(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommunityCreateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = communityService.createCommunity(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Обновить сообщество")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сообщество обновлено"),
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
                    responseCode = "403",
                    description = "Недостаточно прав для изменения сообщества",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Сообщество с таким названием уже существует",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/{id}")
    public ResponseEntity<CommunityResponse> updateCommunity(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommunityUpdateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = communityService.updateCommunity(communityId, userId, request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Удалить сообщество")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Сообщество удалено",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Недостаточно прав для удаления сообщества",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCommunity(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        communityService.deleteCommunity(communityId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Вступить в сообщество")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь вступил в сообщество"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество не найдено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Пользователь уже состоит в сообществе",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{id}/join")
    public ResponseEntity<CommunityResponse> joinCommunity(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        var result = communityService.joinCommunity(communityId, userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Выйти из сообщества")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Пользователь вышел из сообщества",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Владелец не может выйти из собственного сообщества",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество или участник не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveCommunity(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = getUserId(jwt);
        communityService.leaveCommunity(communityId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Получить участников сообщества",
            description = "Доступно только участникам сообщества"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Список участников получен"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Пользователь не состоит в сообществе",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество не найдено",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping("/{id}/members")
    public ResponseEntity<List<CommunityMemberResponse>> getCommunityMembers(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID currentUserId = getUserId(jwt);
        var result = communityService.getCommunityMembers(communityId, currentUserId);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Получить посты сообщества",
            description = "Доступно только участникам сообщества"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Посты сообщества получены"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Пользователь не состоит в сообществе",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество не найдено",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping("/{id}/posts")
    public ResponseEntity<List<PostResponse>> getCommunityPosts(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID currentUserId = getUserId(jwt);
        var result = communityService.getCommunityPosts(communityId, currentUserId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Создать текстовый пост в сообществе")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пост в сообществе создан"),
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
                    responseCode = "403",
                    description = "Пользователь не состоит в сообществе",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Сообщество не найдено",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    @PostMapping("/{id}/posts/text")
    public ResponseEntity<PostResponse> createCommunityTextPost(
            @PathVariable("id") UUID communityId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommunityPostCreateRequest request
    ) {
        UUID userId = getUserId(jwt);
        var result = communityService.createCommunityTextPost(communityId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}