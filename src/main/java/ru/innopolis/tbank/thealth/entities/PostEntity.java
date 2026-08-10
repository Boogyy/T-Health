package ru.innopolis.tbank.thealth.entities;

import jakarta.persistence.*;
import ru.innopolis.tbank.thealth.enums.PostType;
import ru.innopolis.tbank.thealth.enums.PostVisibility;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class PostEntity {

    public PostEntity() {

    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private CommunityEntity community;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private PostVisibility visibility;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_id")
    private WorkoutEntity workout;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private RecipeEntity recipe;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_achievement_id")
    private UserAchievementEntity userAchievement;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private PostType postType;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /*
     * Denormalized counter maintained by a PostgreSQL trigger.
     * The application reads it together with the post, so feed responses
     * contain the correct value without loading the comments themselves.
     */
    @Column(
            name = "comments_count",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private long commentsCount;

    @PrePersist
    private void prePersist() {
        LocalDateTime localDateTime = LocalDateTime.now();

        if (visibility == null) {
            visibility = PostVisibility.PUBLIC;
        }

        if (createdAt == null) {
            createdAt = localDateTime;
        }

        if (updatedAt == null) {
            updatedAt = localDateTime;
        }
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public PostVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(PostVisibility visibility) {
        this.visibility = visibility;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public WorkoutEntity getWorkout() {
        return workout;
    }

    public void setWorkout(WorkoutEntity workout) {
        this.workout = workout;
    }

    public RecipeEntity getRecipe() {
        return recipe;
    }

    public void setRecipe(RecipeEntity recipe) {
        this.recipe = recipe;
    }

    public UserAchievementEntity getUserAchievement() {
        return userAchievement;
    }

    public void setUserAchievement(UserAchievementEntity userAchievement) {
        this.userAchievement = userAchievement;
    }

    public PostType getPostType() {
        return postType;
    }

    public void setPostType(PostType postType) {
        this.postType = postType;
    }

    public CommunityEntity getCommunity() {
        return community;
    }

    public void setCommunity(CommunityEntity community) {
        this.community = community;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getCommentsCount() {
        return commentsCount;
    }
}
