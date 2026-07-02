package ru.innopolis.tbank.thealth.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends THealthException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}