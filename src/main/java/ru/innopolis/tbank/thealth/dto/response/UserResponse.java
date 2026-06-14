package ru.innopolis.tbank.thealth.dto.response;

import ru.innopolis.tbank.thealth.enums.UserRole;

import java.util.UUID;

public record UserResponse (
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    UserRole role
) {
}
