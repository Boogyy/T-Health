package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByKeycloakId(UUID keycloakId);

    // Поиск с учётом регистра по имени пользователя
    Optional<UserEntity> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

}
