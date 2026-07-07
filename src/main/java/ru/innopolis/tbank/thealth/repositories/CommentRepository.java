package ru.innopolis.tbank.thealth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.innopolis.tbank.thealth.entities.CommentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {

    List<CommentEntity> findAllByPost_IdOrderByCreatedAtAsc(UUID postId);

    Optional<CommentEntity> findByIdAndPost_Id(UUID commentId, UUID postId);

    Optional<CommentEntity> findByIdAndAuthor_KeycloakId(UUID commentId, UUID authorId);

    void deleteAllByPost_Id(UUID postId);
}