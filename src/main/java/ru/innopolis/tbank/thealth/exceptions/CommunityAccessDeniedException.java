package ru.innopolis.tbank.thealth.exceptions;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CommunityAccessDeniedException
        extends THealthException {

    public CommunityAccessDeniedException(UUID communityId) {
        super(
                HttpStatus.FORBIDDEN,
                "User is not a member of community: " + communityId
        );
    }
}