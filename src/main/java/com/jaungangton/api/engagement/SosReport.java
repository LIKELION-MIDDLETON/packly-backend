package com.jaungangton.api.engagement;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sos_reports")
class SosReport {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "recommendation_id")
    private UUID recommendationId;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(name = "symptom_labels")
    private String symptomLabels;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SosStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SosReport() {
    }

    SosReport(UUID id, UUID userId, UUID recommendationId, String message, List<String> symptomLabels, Instant now) {
        this.id = id;
        this.userId = userId;
        this.recommendationId = recommendationId;
        this.message = message;
        this.symptomLabels = symptomLabels == null ? "" : String.join(",", symptomLabels);
        this.status = SosStatus.RECEIVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID recommendationId() { return recommendationId; }
    String message() { return message; }
    List<String> symptomLabels() {
        return symptomLabels == null || symptomLabels.isBlank()
                ? List.of() : Arrays.asList(symptomLabels.split(","));
    }
    SosStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
