package ru.innopolis.tbank.thealth.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest (
        @NotBlank
        @Size(max = 64)
        String email,

        @NotBlank
        @Size(max = 256)
        String password,

        @NotBlank
        @Size(max = 32)
        String username,

        @Size(max = 64)
        String firstName,

        @Size(max = 64)
        String lastName
) {
}
