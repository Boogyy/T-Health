package ru.innopolis.tbank.thealth.events;

import java.util.UUID;

public record UserDeletedEvent(UUID keycloakId) {
}