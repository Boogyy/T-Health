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
import ru.innopolis.tbank.thealth.dto.request.UpdateUserRequest;
import ru.innopolis.tbank.thealth.dto.response.ErrorResponse;
import ru.innopolis.tbank.thealth.dto.response.UserResponse;
import ru.innopolis.tbank.thealth.services.UserService;

import java.util.UUID;

@Tag(
        name = "Users",
        description = "Операции с профилем текущего пользователя"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    @Operation(summary = "Получить профиль текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль пользователя получен"),
            @ApiResponse(
                    responseCode = "400",
                    description = "В токене отсутствуют обязательные данные пользователя",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = userService.getCurrentUser(jwt);
        return ResponseEntity.ok(result);
    }



    @Operation(summary = "Обновить профиль текущего пользователя")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль пользователя обновлен"),
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
                    description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Username уже занят",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))

            )
    })
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRequest request
            ) {

        var result = userService.updateCurrentUser(jwt, request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Удалить профиль текущего пользователя")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Профиль пользователя удален",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        userService.deleteUser(jwt);
        return ResponseEntity.noContent().build();
    }
}
