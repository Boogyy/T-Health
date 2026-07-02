package ru.innopolis.tbank.thealth.exceptions;

import org.springframework.http.HttpStatus;

public class NotFoundException extends THealthException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}