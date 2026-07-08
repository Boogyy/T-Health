package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class DirectMessageNotFoundException extends NotFoundException {

    public DirectMessageNotFoundException(UUID messageId) {
        super("Direct message not found by id " + messageId);
    }
}