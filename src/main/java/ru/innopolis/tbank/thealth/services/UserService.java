package ru.innopolis.tbank.thealth.services;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.UpdateUserRequest;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.dto.response.UserResponse;
import ru.innopolis.tbank.thealth.events.UserDeletedEvent;
import ru.innopolis.tbank.thealth.exceptions.DuplicateUsernameException;
import ru.innopolis.tbank.thealth.exceptions.MissingTokenClaimException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.repositories.*;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AchievementService achievementService;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkoutRepository workoutRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final RecipeRepository recipeRepository;
    private final CommentRepository commentRepository;
    private final PostDeletionService postDeletionService;
    private final CommunityDeletionService communityDeletionService;
    private final DirectChatDeletionService directChatDeletionService;



    public UserService (
            UserRepository userRepository,
            AchievementService achievementService,
            ApplicationEventPublisher eventPublisher,
            WorkoutRepository workoutRepository,
            FoodEntryRepository foodEntryRepository,
            UserAchievementRepository userAchievementRepository,
            RecipeRepository recipeRepository,
            CommentRepository commentRepository,
            PostDeletionService postDeletionService,
            CommunityDeletionService communityDeletionService,
            DirectChatDeletionService directChatDeletionService
    ) {
        this.userRepository = userRepository;
        this.achievementService = achievementService;
        this.eventPublisher = eventPublisher;
        this.workoutRepository = workoutRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.recipeRepository = recipeRepository;
        this.commentRepository = commentRepository;
        this.postDeletionService = postDeletionService;
        this.communityDeletionService = communityDeletionService;
        this.directChatDeletionService = directChatDeletionService;
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
                .orElseThrow(() -> new UserNotFoundException(keycloakId));

        if (request.username() != null && !request.username().isBlank()) {
            if (!request.username().equals(user.getUsername())
                    && userRepository.existsByUsername(request.username())) {
                throw new DuplicateUsernameException(request.username());
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
            throw new MissingTokenClaimException(email);
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
                .orElseThrow(() -> new UserNotFoundException(keycloakId));

        /*
         * Сообщества пользователя-владельца:
         * посты всех участников, комментарии, memberships
         */
        communityDeletionService
                .deleteAllOwnedCommunities(keycloakId);

        //membership пользователя в сообществах других владельцев
        communityDeletionService
                .removeUserFromOtherCommunities(keycloakId);

        // Личные чаты пользователя и все сообщения этих чатов
        directChatDeletionService
                .deleteAllChatsByUser(keycloakId);

        // Комментарии пользователя под чужими и собственными постами
        commentRepository.deleteAllByAuthor_KeycloakId(keycloakId);

        // Все посты пользователя и оставшиеся комментарии других пользователей под этими постами
        postDeletionService.deleteAllPostsByUser(keycloakId);

        workoutRepository.deleteAllByUser_KeycloakId(keycloakId);
        foodEntryRepository.deleteAllByUser_KeycloakId(keycloakId);
        recipeRepository.deleteAllByUser_KeycloakId(keycloakId);
        userAchievementRepository.deleteAllByUser_KeycloakId(keycloakId);

        userRepository.delete(user);

        userRepository.flush();

        eventPublisher.publishEvent(new UserDeletedEvent(keycloakId));
    }

}
