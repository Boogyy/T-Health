package ru.innopolis.tbank.thealth.exceptions;

public class CommunityAlreadyExistsException extends ConflictException {

    public CommunityAlreadyExistsException(String communityName) {
        super("Community already exists with name: " + communityName);
    }
}