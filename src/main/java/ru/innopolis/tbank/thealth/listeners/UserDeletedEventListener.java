package ru.innopolis.tbank.thealth.listeners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.innopolis.tbank.thealth.events.UserDeletedEvent;
import ru.innopolis.tbank.thealth.services.KeycloakAdminClient;

@Component
@Slf4j
public class UserDeletedEventListener {

    private final KeycloakAdminClient keycloakAdminClient;

    public UserDeletedEventListener(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleUserDeleted(UserDeletedEvent event) {
        try {
            keycloakAdminClient.deleteUser(event.keycloakId());

            log.info(
                    "User {} was deleted from Keycloak",
                    event.keycloakId()
            );
        } catch (RuntimeException exception) {
            // PostgreSQL уже успешно закоммичен, поэтому откатить удаление локальных данных нельзя
            log.error(
                    "Local user {} was deleted, but Keycloak deletion failed",
                    event.keycloakId(),
                    exception
            );
        }
    }
}