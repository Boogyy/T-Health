package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.innopolis.tbank.thealth.events.UserDeletedEvent;
import ru.innopolis.tbank.thealth.listeners.UserDeletedEventListener;
import ru.innopolis.tbank.thealth.services.KeycloakAdminClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDeletedEventListenerTest {

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserDeletedEventListener listener;

    @Test
    void handleUserDeleted_deletesUserFromKeycloak() {
        UUID userId = UUID.randomUUID();

        listener.handleUserDeleted(new UserDeletedEvent(userId));

        verify(keycloakAdminClient).deleteUser(userId);
    }

    @Test
    void handleUserDeleted_keycloakFailure_doesNotPropagateException() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("Keycloak unavailable"))
                .when(keycloakAdminClient)
                .deleteUser(userId);

        assertThatCode(() ->
                listener.handleUserDeleted(new UserDeletedEvent(userId))
        ).doesNotThrowAnyException();
    }
}
