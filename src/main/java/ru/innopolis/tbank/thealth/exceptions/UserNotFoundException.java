package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(UUID userId) {
        super("User not found by id " + userId);
    }
}