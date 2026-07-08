package ru.innopolis.tbank.thealth.services;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.DailyFoodEntriesResponse;
import ru.innopolis.tbank.thealth.dto.response.FoodEntryResponse;
import ru.innopolis.tbank.thealth.entities.FoodEntryEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.FoodEntryNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.repositories.FoodEntryRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FoodEntryService {

    private final FoodEntryRepository foodEntryRepository;
    private final UserRepository userRepository;
    private final AchievementService achievementService;

    public FoodEntryService(FoodEntryRepository foodEntryRepository,
                            UserRepository userRepository,
                            AchievementService achievementService) {
        this.foodEntryRepository = foodEntryRepository;
        this.userRepository = userRepository;
        this.achievementService = achievementService;
    }

    @Transactional(readOnly = true)
    public List<FoodEntryResponse> getCurrentUserFoodEntries(UUID userId) {
        return foodEntryRepository.findAllByUser_KeycloakIdOrderByMealDateDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FoodEntryResponse getFoodEntry(UUID foodEntryId, UUID userId) {
        FoodEntryEntity foodEntry = findOwnedFoodEntry(foodEntryId, userId);
        return toResponse(foodEntry);
    }

    @Transactional
    public FoodEntryResponse createFoodEntry(FoodEntryCreateRequest request, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        FoodEntryEntity foodEntry = new FoodEntryEntity();
        foodEntry.setUser(user);
        foodEntry.setMealName(request.mealName());
        foodEntry.setCalories(request.calories());
        foodEntry.setProteins(request.proteins());
        foodEntry.setFats(request.fats());
        foodEntry.setCarbohydrates(request.carbohydrates());
        foodEntry.setMealDate(request.mealDate());

        FoodEntryEntity savedFoodEntry = foodEntryRepository.save(foodEntry);

        grantFoodEntryAchievements(userId);

        return toResponse(savedFoodEntry);
    }

    @Transactional
    public FoodEntryResponse updateFoodEntry(
            UUID foodEntryId,
            UUID userId,
            FoodEntryUpdateRequest request
    ) {
        FoodEntryEntity foodEntry = findOwnedFoodEntry(foodEntryId, userId);

        if (request.mealName() != null && !request.mealName().isBlank()) {
            foodEntry.setMealName(request.mealName());
        }

        if (request.calories() != null) {
            foodEntry.setCalories(request.calories());
        }

        if (request.proteins() != null) {
            foodEntry.setProteins(request.proteins());
        }

        if (request.fats() != null) {
            foodEntry.setFats(request.fats());
        }

        if (request.carbohydrates() != null) {
            foodEntry.setCarbohydrates(request.carbohydrates());
        }

        if (request.mealDate() != null) {
            foodEntry.setMealDate(request.mealDate());
        }

        return toResponse(foodEntry);
    }

    @Transactional
    public void deleteFoodEntry(UUID foodEntryId, UUID userId) {
        FoodEntryEntity foodEntry = findOwnedFoodEntry(foodEntryId, userId);
        foodEntryRepository.delete(foodEntry);
    }

    @Transactional(readOnly = true)
    public DailyFoodEntriesResponse getDailyFoodEntries(Jwt jwt, LocalDate date) {
        UUID userId = UUID.fromString(jwt.getSubject());

        LocalDate selectedDate = date == null ? LocalDate.now() : date;

        LocalDateTime start = selectedDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<FoodEntryEntity> entries = foodEntryRepository
                .findAllByUser_KeycloakIdAndMealDateGreaterThanEqualAndMealDateLessThanOrderByMealDateAsc(
                        userId,
                        start,
                        end
                );

        Integer totalCalories = entries.stream()
                .map(FoodEntryEntity::getCalories)
                .filter(value -> value != null)
                .reduce(0, Integer::sum);

        BigDecimal totalProteins = entries.stream()
                .map(FoodEntryEntity::getProteins)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFats = entries.stream()
                .map(FoodEntryEntity::getFats)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCarbohydrates = entries.stream()
                .map(FoodEntryEntity::getCarbohydrates)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FoodEntryResponse> responses = entries.stream()
                .map(this::toResponse)
                .toList();

        return new DailyFoodEntriesResponse(
                selectedDate,
                totalCalories,
                totalProteins,
                totalFats,
                totalCarbohydrates,
                responses
        );
    }

    private FoodEntryEntity findOwnedFoodEntry(UUID foodEntryId, UUID userId) {
        return foodEntryRepository.findByIdAndUser_KeycloakId(foodEntryId, userId)
                .orElseThrow(() -> new FoodEntryNotFoundException(foodEntryId));
    }

    private void grantFoodEntryAchievements(UUID userId) {
        long foodEntriesCount = foodEntryRepository.countByUser_KeycloakId(userId);

        if (foodEntriesCount >= 1) {
            achievementService.grantAchievementIfNotExists(userId, "FIRST_FOOD_ENTRY");
        }

        if (foodEntriesCount >= 10) {
            achievementService.grantAchievementIfNotExists(userId, "TEN_FOOD_ENTRIES");
        }
    }

    private FoodEntryResponse toResponse(FoodEntryEntity foodEntry) {
        return new FoodEntryResponse(
                foodEntry.getId(),
                foodEntry.getUser().getKeycloakId(),
                foodEntry.getMealName(),
                foodEntry.getCalories(),
                foodEntry.getProteins(),
                foodEntry.getFats(),
                foodEntry.getCarbohydrates(),
                foodEntry.getMealDate(),
                foodEntry.getCreatedAt(),
                foodEntry.getUpdatedAt()
        );
    }


}