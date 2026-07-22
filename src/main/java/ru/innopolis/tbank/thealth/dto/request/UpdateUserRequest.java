package ru.innopolis.tbank.thealth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на обновление профиля пользователя")
public record UpdateUserRequest(

        @Schema(description = "Username пользователя", example = "testuser")
        @Size(max = 32)
        @Pattern(regexp = "(?s).*\\S.*", message = "Username must not be blank")
        String username,

        @Schema(description = "Имя пользователя", example = "Ivan")
        @Size(max = 64)
        String firstName,

        @Schema(description = "Фамилия пользователя", example = "Ivanov")
        @Size(max = 64)
        String lastName
) {
}
