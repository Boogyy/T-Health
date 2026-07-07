package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class CommunityNotFoundException extends NotFoundException {

    public CommunityNotFoundException(UUID communityId) {
        super("Community not found by id " + communityId);
    }
}