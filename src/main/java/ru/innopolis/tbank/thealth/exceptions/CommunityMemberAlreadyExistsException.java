package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class CommunityMemberAlreadyExistsException extends ConflictException {

    public CommunityMemberAlreadyExistsException(UUID communityId) {
        super("User is already a member of community " + communityId);
    }
}