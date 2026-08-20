package com.jaungangton.api.engagement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_usage_completions")
class ProductUsageCompletion {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "recommendation_product_id", nullable = false)
    private UUID recommendationProductId;
    @Column(name = "used_on", nullable = false)
    private LocalDate usedOn;
    @Column(nullable = false)
    private boolean completed;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductUsageCompletion() {
    }

    ProductUsageCompletion(UUID id, UUID userId, UUID recommendationProductId,
                           LocalDate usedOn, boolean completed, Instant now) {
        this.id = id;
        this.userId = userId;
        this.recommendationProductId = recommendationProductId;
        this.usedOn = usedOn;
        this.completed = completed;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(boolean completed, Instant now) {
        if (this.completed != completed) {
            this.completed = completed;
            this.updatedAt = now;
        }
    }

    UUID id() { return id; }
    UUID recommendationProductId() { return recommendationProductId; }
    LocalDate usedOn() { return usedOn; }
    boolean completed() { return completed; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
