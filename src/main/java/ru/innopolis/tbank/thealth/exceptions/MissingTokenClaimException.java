package ru.innopolis.tbank.thealth.exceptions;

public class MissingTokenClaimException extends BadRequestException {
    public MissingTokenClaimException(String claimName) {
        super("Required token claim is missing: " + claimName);
    }
}
