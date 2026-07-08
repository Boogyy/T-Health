package ru.innopolis.tbank.thealth.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "direct_messages")
public class DirectMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private DirectChatEntity chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    public DirectMessageEntity() {
    }

    @PrePersist
    private void prePersist() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DirectChatEntity getChat() {
        return chat;
    }

    public void setChat(DirectChatEntity chat) {
        this.chat = chat;
    }

    public UserEntity getSender() {
        return sender;
    }

    public void setSender(UserEntity sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}