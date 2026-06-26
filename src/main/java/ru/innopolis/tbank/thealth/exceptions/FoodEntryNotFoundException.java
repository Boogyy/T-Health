package ru.innopolis.tbank.thealth.exceptions;

import java.util.UUID;

public class FoodEntryNotFoundException extends NotFoundException {

    public FoodEntryNotFoundException(UUID foodEntryId) {
        super("Food entry not found by id " + foodEntryId);
    }
}