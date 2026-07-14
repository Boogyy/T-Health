package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.DirectMessageEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessageEntity, UUID> {

    List<DirectMessageEntity> findAllByChat_IdOrderBySentAtAsc(UUID chatId);

    Optional<DirectMessageEntity> findTopByChat_IdOrderBySentAtDesc(UUID chatId);

    void deleteAllByChat_Id(UUID chatId);
}