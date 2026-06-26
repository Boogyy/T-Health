package ru.innopolis.tbank.thealth.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends THealthException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}