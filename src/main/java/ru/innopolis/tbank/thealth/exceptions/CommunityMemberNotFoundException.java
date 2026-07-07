package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class CommunityMemberNotFoundException extends NotFoundException {

    public CommunityMemberNotFoundException(UUID communityId) {
        super("Current user is not a member of community " + communityId);
    }
}