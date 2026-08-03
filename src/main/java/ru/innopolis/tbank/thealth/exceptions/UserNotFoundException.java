package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(UUID userId) {
        super("User not found by id " + userId);
    }

    public UserNotFoundException(String username) {
        super("User not found by username " + username);
    }
}