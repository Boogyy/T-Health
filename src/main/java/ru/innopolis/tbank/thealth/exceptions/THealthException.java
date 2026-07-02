package ru.innopolis.tbank.thealth.exceptions;

import org.springframework.http.HttpStatus;

public abstract class THealthException extends RuntimeException {

    private final HttpStatus status;

    protected THealthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

}