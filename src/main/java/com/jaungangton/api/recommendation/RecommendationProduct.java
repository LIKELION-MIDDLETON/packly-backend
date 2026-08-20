package com.jaungangton.api.recommendation;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.jaungangton.api.ai.ProductSlotNormalizer;

@Entity
@Table(name = "recommendation_products")
public class RecommendationProduct {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;
    @Column(name = "order_index", nullable = false)
    private short orderIndex;
    @Column(name = "display_order", nullable = false)
    private short displayOrder;
    @Column(name = "application_order")
    private Short applicationOrder;
    @Column(name = "usage_group", nullable = false, length = 32)
    private String usageGroup;
    @Column(nullable = false, length = 100)
    private String slot;
    @Column(name = "goods_no", nullable = false, length = 100)
    private String goodsNo;
    @Column(nullable = false, length = 255)
    private String brand;
    @Column(nullable = false, length = 500)
    private String name;
    @Column
    private Long price;
    @Column(name = "daily_price")
    private Long dailyPrice;
    @Column(name = "daily_volume", length = 100)
    private String dailyVolume;
    @Column(name = "total_volume", length = 100)
    private String totalVolume;
    @Column(name = "sale_price")
    private Long salePrice;
    @Column(name = "recommendation_reason", columnDefinition = "TEXT")
    private String recommendationReason;
    @Column(columnDefinition = "TEXT")
    private String suitability;
    @Column(name = "suitability_source", length = 32)
    private String suitabilitySource;
    @Column(name = "functional_info", columnDefinition = "TEXT")
    private String functionalInfo;
    @Column(nullable = false)
    private boolean unscented;
    @Column(name = "comedogenic_score", nullable = false)
    private int comedogenicScore;

    protected RecommendationProduct() {
    }

    RecommendationProduct(UUID id, Recommendation recommendation, int orderIndex, String slot, String goodsNo,
            String brand, String name, long price, String suitability, String suitabilitySource,
            String functionalInfo, boolean unscented, int comedogenicScore) {
        this(id, recommendation, orderIndex, ProductSlotNormalizer.applicationOrder(orderIndex),
                ProductSlotNormalizer.usageGroup(orderIndex, slot), slot, goodsNo, brand, name, price, suitability,
                suitabilitySource, functionalInfo, unscented, comedogenicScore,
                null, null, null, null, null);
    }

    RecommendationProduct(UUID id, Recommendation recommendation, int displayOrder, Integer applicationOrder,
            String usageGroup, String slot, String goodsNo, String brand, String name, Long price, String suitability,
            String suitabilitySource, String functionalInfo, boolean unscented, int comedogenicScore,
            Long dailyPrice, String dailyVolume, String totalVolume, Long salePrice, String recommendationReason) {
        this.id = id;
        this.recommendation = recommendation;
        this.orderIndex = (short) displayOrder;
        this.displayOrder = (short) displayOrder;
        this.applicationOrder = applicationOrder == null ? null : applicationOrder.shortValue();
        this.usageGroup = usageGroup;
        this.slot = slot;
        this.goodsNo = goodsNo;
        this.brand = brand;
        this.name = name;
        this.price = price;
        this.suitability = suitability;
        this.suitabilitySource = suitabilitySource;
        this.functionalInfo = functionalInfo;
        this.unscented = unscented;
        this.comedogenicScore = comedogenicScore;
        this.dailyPrice = dailyPrice;
        this.dailyVolume = dailyVolume;
        this.totalVolume = totalVolume;
        this.salePrice = salePrice;
        this.recommendationReason = recommendationReason;
    }

    /** Legacy constructor for the pre-PR#9 product contract. */
    RecommendationProduct(UUID id, Recommendation recommendation, int displayOrder, Integer applicationOrder,
            String usageGroup, String slot, String goodsNo, String brand, String name, long price, String suitability,
            String suitabilitySource, String functionalInfo, boolean unscented, int comedogenicScore) {
        this(id, recommendation, displayOrder, applicationOrder, usageGroup, slot, goodsNo, brand, name,
                Long.valueOf(price), suitability, suitabilitySource, functionalInfo, unscented, comedogenicScore,
                null, null, null, null, null);
    }

    public UUID id() { return id; }
    public Recommendation recommendation() { return recommendation; }
    public int orderIndex() { return orderIndex; }
    public int displayOrder() { return displayOrder; }
    public Integer applicationOrder() { return applicationOrder == null ? null : applicationOrder.intValue(); }
    public String usageGroup() { return usageGroup; }
    public String slot() { return slot; }
    public String goodsNo() { return goodsNo; }
    public String brand() { return brand; }
    public String name() { return name; }
    public Long price() { return price; }
    public Long dailyPrice() { return dailyPrice; }
    public String dailyVolume() { return dailyVolume; }
    public String totalVolume() { return totalVolume; }
    public Long salePrice() { return salePrice; }
    public String recommendationReason() { return recommendationReason; }
    public String suitability() { return suitability; }
    public String suitabilitySource() { return suitabilitySource; }
    public String functionalInfo() { return functionalInfo; }
    public boolean unscented() { return unscented; }
    public int comedogenicScore() { return comedogenicScore; }
}
