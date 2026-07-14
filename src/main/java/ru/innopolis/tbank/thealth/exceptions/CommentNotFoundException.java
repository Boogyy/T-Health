package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class CommentNotFoundException extends NotFoundException {

    public CommentNotFoundException(UUID commentId) {
        super("Comment not found by id " + commentId);
    }
}