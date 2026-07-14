package ru.innopolis.tbank.thealth.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.TestcontainersConfiguration;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.KeycloakAdminClient;
import ru.innopolis.tbank.thealth.support.TestFixtures;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @MockBean
    protected JwtDecoder jwtDecoder;

    @MockBean
    protected KeycloakAdminClient keycloakAdminClient;

    protected UserEntity persistUser(UUID id, String username) {
        return userRepository.saveAndFlush(TestFixtures.user(id, username));
    }

    protected RequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("email", "test@example.com")
                .claim("preferred_username", "testuser")
                .claim("given_name", "Test")
                .claim("family_name", "User")
        );
    }
}
