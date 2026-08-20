package com.jaungangton.api.engagement;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendation_feedback")
class RecommendationFeedback {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;
    @Column(nullable = false)
    private short rating;
    @Column(length = 2000)
    private String comment;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecommendationFeedback() {
    }

    RecommendationFeedback(UUID id, UUID userId, UUID recommendationId,
                           int rating, String comment, Instant now) {
        this.id = id;
        this.userId = userId;
        this.recommendationId = recommendationId;
        this.rating = (short) rating;
        this.comment = comment;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(int rating, String comment, Instant now) {
        if (this.rating != rating || !Objects.equals(this.comment, comment)) {
            this.rating = (short) rating;
            this.comment = comment;
            this.updatedAt = now;
        }
    }

    UUID id() { return id; }
    UUID recommendationId() { return recommendationId; }
    int rating() { return rating; }
    String comment() { return comment; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
