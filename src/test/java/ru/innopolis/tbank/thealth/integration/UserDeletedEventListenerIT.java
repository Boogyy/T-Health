package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.innopolis.tbank.thealth.TestcontainersConfiguration;
import ru.innopolis.tbank.thealth.events.UserDeletedEvent;
import ru.innopolis.tbank.thealth.services.KeycloakAdminClient;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserDeletedEventListenerIT {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private KeycloakAdminClient keycloakAdminClient;

    @Test
    void publishedEvent_committedTransaction_callsKeycloakAfterCommit() {
        UUID userId = UUID.randomUUID();
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new UserDeletedEvent(userId))
        );

        verify(keycloakAdminClient).deleteUser(userId);
    }

    @Test
    void publishedEvent_rolledBackTransaction_doesNotCallKeycloak() {
        UUID userId = UUID.randomUUID();
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new UserDeletedEvent(userId));
            status.setRollbackOnly();
        });

        verifyNoInteractions(keycloakAdminClient);
    }
}
