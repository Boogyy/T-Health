package ru.innopolis.tbank.thealth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.innopolis.tbank.thealth.enums.UserRole;

import java.util.UUID;

@Schema(description = "Профиль пользователя")
public record UserResponse(

        @Schema(description = "Идентификатор пользователя из Keycloak", example = "ccd11ba4-3a88-42cb-82f7-19d9e4fdb478")
        UUID id,

        @Schema(description = "Username пользователя", example = "testuser")
        String username,

        @Schema(description = "Email пользователя", example = "testuser@example.com")
        String email,

        @Schema(description = "Имя пользователя", example = "Ivan")
        String firstName,

        @Schema(description = "Фамилия пользователя", example = "Ivanov")
        String lastName
) {
}
