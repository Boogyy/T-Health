package ru.innopolis.tbank.thealth.services;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.UpdateUserRequest;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.dto.response.UserResponse;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AchievementService achievementService;
    private final KeycloakAdminClient keycloakAdminClient;
     
    public UserService (
            UserRepository userRepository,
            AchievementService achievementService,
            KeycloakAdminClient keycloakAdminClient
    ) {
        this.userRepository = userRepository;
        this.achievementService = achievementService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @Transactional
    public UserResponse getCurrentUser(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        UserEntity user = userRepository.findById(keycloakId)
                .orElseGet(() -> createUserFromJwt(jwt, keycloakId));

        return toResponse(user);
    }

    @Transactional
    public UserResponse updateCurrentUser(Jwt jwt, UpdateUserRequest request) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());

        UserEntity user = userRepository.findById(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found by id " + keycloakId));

        if (request.username() != null && !request.username().isBlank()) {
            if (!request.username().equals(user.getUsername())
                    && userRepository.existsByUsername(request.username())) {
                throw new IllegalArgumentException("Username already exists");
            }

            user.setUsername(request.username());
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }

        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }

        return toResponse(user);
    }


    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getKeycloakId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }

    private UserEntity createUserFromJwt(Jwt jwt, UUID keycloakId) {
        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email claim is missing in token");
        }

        if (username == null || username.isBlank()) {
            username = "user_" + keycloakId;
        }

        UserEntity newUser = new UserEntity();
        newUser.setKeycloakId(keycloakId);
        newUser.setEmail(email);
        newUser.setUsername(username);
        newUser.setFirstName(jwt.getClaimAsString("given_name"));
        newUser.setLastName(jwt.getClaimAsString("family_name"));

        UserEntity savedUser = userRepository.save(newUser);
        achievementService.grantAchievementIfNotExists(
                savedUser.getKeycloakId(),
                "WELCOME_TO_T_HEALTH"
        );

        return savedUser;
    }


    @Transactional
    public void deleteUser(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());

        UserEntity user = userRepository.findById(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        keycloakAdminClient.deleteUser(keycloakId);

        userRepository.delete(user);
    }

}
