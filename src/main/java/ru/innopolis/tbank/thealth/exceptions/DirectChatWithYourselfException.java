package ru.innopolis.tbank.thealth.exceptions;

public class DirectChatWithYourselfException extends BadRequestException {

    public DirectChatWithYourselfException() {
        super("User cannot create direct chat with himself");
    }
}