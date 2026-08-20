package com.jaungangton.api.survey;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "surveys")
public class Survey {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "skin_type", nullable = false)
    private int skinType;

    @Column(nullable = false)
    private String concerns;

    @Column(nullable = false)
    private int duration;

    @Column(nullable = false)
    private String areas;

    @Column(nullable = false)
    private int irritation;

    @Column(nullable = false)
    private int diagnosed;

    @Column(name = "diagnosed_text")
    private String otherDiagnosis;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Survey() {
    }

    Survey(UUID id, UUID userId) {
        this.id = id;
        this.userId = userId;
    }

    void update(SurveyRequest request, SurveyMapper mapper, Instant now) {
        SurveyNumericSnapshot numeric = mapper.toNumeric(request);
        skinType = numeric.skinType();
        concerns = mapper.toCsv(request.concerns());
        duration = numeric.duration();
        areas = mapper.toCsv(request.areas());
        irritation = numeric.irritation();
        diagnosed = numeric.diagnosed();
        otherDiagnosis = request.otherDiagnosis() == null ? null : request.otherDiagnosis().trim();
        submittedAt = now;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    int skinType() {
        return skinType;
    }

    String concerns() {
        return concerns;
    }

    int duration() {
        return duration;
    }

    String areas() {
        return areas;
    }

    int irritation() {
        return irritation;
    }

    int diagnosed() {
        return diagnosed;
    }

    String otherDiagnosis() {
        return otherDiagnosis;
    }

    Instant submittedAt() {
        return submittedAt;
    }
}
