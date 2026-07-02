package ru.innopolis.tbank.thealth.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_achievements")
public class UserAchievementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    private AchievementEntity achievement;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    public UserAchievementEntity() {
    }

    @PrePersist
    private void prePersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public AchievementEntity getAchievement() {
        return achievement;
    }

    public void setAchievement(AchievementEntity achievement) {
        this.achievement = achievement;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}