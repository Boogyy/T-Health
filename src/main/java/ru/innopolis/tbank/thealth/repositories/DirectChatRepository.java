package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.innopolis.tbank.thealth.entities.DirectChatEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectChatRepository extends JpaRepository<DirectChatEntity, UUID> {

    @Query("""
            SELECT chat FROM DirectChatEntity chat
            WHERE chat.firstUser.keycloakId = :userId
               OR chat.secondUser.keycloakId = :userId
            ORDER BY chat.createdAt DESC
            """)
    List<DirectChatEntity> findAllByParticipantId(@Param("userId") UUID userId);

    @Query("""
            SELECT chat FROM DirectChatEntity chat
            WHERE chat.id = :chatId
              AND (
                    chat.firstUser.keycloakId = :userId
                 OR chat.secondUser.keycloakId = :userId
              )
            """)
    Optional<DirectChatEntity> findByIdAndParticipantId(
            @Param("chatId") UUID chatId,
            @Param("userId") UUID userId
    );

    Optional<DirectChatEntity> findByFirstUser_KeycloakIdAndSecondUser_KeycloakId(
            UUID firstUserId,
            UUID secondUserId
    );
}