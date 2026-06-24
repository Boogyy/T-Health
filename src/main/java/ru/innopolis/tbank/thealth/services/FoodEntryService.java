package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.FoodEntryUpdateRequest;
import ru.innopolis.tbank.thealth.dto.response.FoodEntryResponse;
import ru.innopolis.tbank.thealth.entities.FoodEntryEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.repositories.FoodEntryRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class FoodEntryService {

    private final FoodEntryRepository foodEntryRepository;
    private final UserRepository userRepository;

    public FoodEntryService(FoodEntryRepository foodEntryRepository,
                            UserRepository userRepository) {
        this.foodEntryRepository = foodEntryRepository;
        this.userRepository = userRepository;
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
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        FoodEntryEntity foodEntry = new FoodEntryEntity();
        foodEntry.setUser(user);
        foodEntry.setMealName(request.mealName());
        foodEntry.setCalories(request.calories());
        foodEntry.setProteins(request.proteins());
        foodEntry.setFats(request.fats());
        foodEntry.setCarbohydrates(request.carbohydrates());
        foodEntry.setMealDate(request.mealDate());

        FoodEntryEntity savedFoodEntry = foodEntryRepository.save(foodEntry);

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

    private FoodEntryEntity findOwnedFoodEntry(UUID foodEntryId, UUID userId) {
        return foodEntryRepository.findByIdAndUser_KeycloakId(foodEntryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Food entry not found"));
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