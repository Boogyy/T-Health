package ru.innopolis.tbank.thealth.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest (

        @Size(max = 32)
        String username,

        @Size(max = 64)
        String firstName,

        @Size(max = 64)
        String lastName
) {
}
