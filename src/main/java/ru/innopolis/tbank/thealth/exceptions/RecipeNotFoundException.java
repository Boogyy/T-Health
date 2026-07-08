package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class RecipeNotFoundException extends NotFoundException {

    public RecipeNotFoundException(UUID recipeId) {
        super("Recipe not found by id " + recipeId);
    }
}