package ru.innopolis.tbank.thealth.services;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import ru.innopolis.tbank.thealth.dto.request.WorkoutPostCreateRequest;
import ru.innopolis.tbank.thealth.entities.PostEntity;
import ru.innopolis.tbank.thealth.entities.UserAchievementEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.entities.WorkoutEntity;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.exceptions.AchievementUserNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.repositories.PostRepository;
import ru.innopolis.tbank.thealth.repositories.UserAchievementRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.repositories.WorkoutRepository;

import java.util.UUID;

@Service
public class PostService {

    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;
    private final PostRepository postRepository;

    public PostService(
            UserAchievementRepository userAchievementRepository,
            UserRepository userRepository,
            WorkoutRepository workoutRepository, PostRepository postRepository) {
        this.userAchievementRepository = userAchievementRepository;
        this.userRepository = userRepository;
        this.workoutRepository = workoutRepository;
        this.postRepository = postRepository;
    }

    public Object createAchievementPost(UUID id) {
        return null;
    }

    public Object postAchievement(UUID id, Jwt jwt) {
        UserAchievementEntity check = userAchievementRepository
                .findByIdAndUser_KeycloakId(id, getKeycloakId(jwt))
                .orElseThrow(() -> new AchievementUserNotFoundException(id));


        return null;
    }

    public Object postFood(UUID id) {
        return null;
    }

    public Object postWorkout(UUID id) {
        return null;
    }

    public Object createFoodEntryPost(Jwt jwt, WorkoutPostCreateRequest workoutPostCreateRequest) {
        return null;
    }

    public PostEntity createWorkoutEntryPost(
            Jwt jwt,
            WorkoutPostCreateRequest workoutPostCreateRequest
    ) {
        UserEntity user = userRepository.findByKeycloakId(getKeycloakId(jwt))
                .orElseThrow(() -> new UserNotFoundException(getKeycloakId(jwt)));

        WorkoutEntity workout = new WorkoutEntity();
        workout.setUser(user);
        workout.setTitle(workoutPostCreateRequest.title());
        workout.setType(workoutPostCreateRequest.type());
        workout.setDescription(workoutPostCreateRequest.description());
        workout.setDurationMinutes(workoutPostCreateRequest.durationMinutes());
        workout.setCaloriesBurned(workoutPostCreateRequest.caloriesBurned());

        WorkoutEntity savedWorkout = workoutRepository.save(workout);

        PostEntity postEntity = new PostEntity();
        postEntity.setUser(user);
        postEntity.setWorkout(savedWorkout);
        postEntity.setPostType(PostType.WORKOUT);
        postEntity.setTitle(workoutPostCreateRequest.postTitle());

        PostEntity savedPost = postRepository.save(postEntity);
        return savedPost;
    }

    private UUID getKeycloakId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
