package com.jaungangton.api.recommendation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendations")
public class Recommendation {
    @Id
    private UUID id;
    @Column(name = "analysis_id", nullable = false, unique = true)
    private UUID analysisId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 100)
    private String diagnosis;
    @Column(nullable = false, length = 500)
    private String headline;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;
    @Column(nullable = false)
    private double confidence;
    @Column(nullable = false, length = 64)
    private String triage;
    @Column(name = "medical_recommended", nullable = false)
    private boolean medicalRecommended;
    @Column(name = "medical_reasons", nullable = false, columnDefinition = "TEXT")
    private String medicalReasons;
    @Column(name = "reflected_survey", columnDefinition = "TEXT")
    private String reflectedSurvey;
    @Column(name = "total_price")
    private Long totalPrice;
    @Column(name = "total_price_daily")
    private Long totalPriceDaily;
    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;
    @Column(name = "care_recommendations", nullable = false, columnDefinition = "TEXT")
    private String careRecommendations;
    @Column(columnDefinition = "TEXT")
    private String disclaimer;
    @Column(name = "ai_request_snapshot", nullable = false, columnDefinition = "TEXT")
    private String aiRequestSnapshot;
    @Column(name = "ai_response_snapshot", nullable = false, columnDefinition = "TEXT")
    private String aiResponseSnapshot;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<RecommendationProduct> products = new ArrayList<>();

    protected Recommendation() {
    }

    Recommendation(UUID id, UUID analysisId, UUID userId, String diagnosis, String headline, String summary,
            double confidence, String triage, boolean medicalRecommended, String medicalReasons,
            String reflectedSurvey, Long totalPrice, Long totalPriceDaily, String analysisSummary, String careRecommendations,
            String disclaimer, String aiRequestSnapshot, String aiResponseSnapshot, Instant createdAt) {
        this.id = id;
        this.analysisId = analysisId;
        this.userId = userId;
        this.diagnosis = diagnosis;
        this.headline = headline;
        this.summary = summary;
        this.confidence = confidence;
        this.triage = triage;
        this.medicalRecommended = medicalRecommended;
        this.medicalReasons = medicalReasons;
        this.reflectedSurvey = reflectedSurvey;
        this.totalPrice = totalPrice;
        this.totalPriceDaily = totalPriceDaily;
        this.analysisSummary = analysisSummary;
        this.careRecommendations = careRecommendations;
        this.disclaimer = disclaimer;
        this.aiRequestSnapshot = aiRequestSnapshot;
        this.aiResponseSnapshot = aiResponseSnapshot;
        this.createdAt = createdAt;
    }

    /** Legacy constructor for existing tests and pre-PR#9 callers. */
    Recommendation(UUID id, UUID analysisId, UUID userId, String diagnosis, String headline, String summary,
            double confidence, String triage, boolean medicalRecommended, String medicalReasons,
            String reflectedSurvey, long totalPrice, String analysisSummary, String careRecommendations,
            String disclaimer, String aiRequestSnapshot, String aiResponseSnapshot, Instant createdAt) {
        this(id, analysisId, userId, diagnosis, headline, summary, confidence, triage, medicalRecommended,
                medicalReasons, reflectedSurvey, totalPrice, null, analysisSummary, careRecommendations,
                disclaimer, aiRequestSnapshot, aiResponseSnapshot, createdAt);
    }

    void addProduct(RecommendationProduct product) {
        products.add(product);
    }

    public UUID id() { return id; }
    public UUID analysisId() { return analysisId; }
    public UUID userId() { return userId; }
    public String diagnosis() { return diagnosis; }
    public String headline() { return headline; }
    public String summary() { return summary; }
    public double confidence() { return confidence; }
    public String triage() { return triage; }
    public boolean medicalRecommended() { return medicalRecommended; }
    public String medicalReasons() { return medicalReasons; }
    public String reflectedSurvey() { return reflectedSurvey; }
    public Long totalPrice() { return totalPrice; }
    public Long totalPriceDaily() { return totalPriceDaily; }
    public String analysisSummary() { return analysisSummary; }
    public String careRecommendations() { return careRecommendations; }
    public String disclaimer() { return disclaimer; }
    public String aiRequestSnapshot() { return aiRequestSnapshot; }
    public String aiResponseSnapshot() { return aiResponseSnapshot; }
    public Instant createdAt() { return createdAt; }
    public List<RecommendationProduct> products() { return List.copyOf(products); }
}
