package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class DirectChatNotFoundException extends NotFoundException {

    public DirectChatNotFoundException(UUID chatId) {
        super("Direct chat not found by id " + chatId);
    }
}