package ru.innopolis.tbank.thealth.exceptions;

public class DuplicateUsernameException extends ConflictException {

    public DuplicateUsernameException(String username) {
        super("Username already exists: " + username);
    }
}