package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class PostNotFoundException extends NotFoundException {

    public PostNotFoundException(UUID postId) {
        super("Post not found by id " + postId);
    }
}