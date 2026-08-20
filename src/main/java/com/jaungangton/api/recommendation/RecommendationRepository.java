package com.jaungangton.api.recommendation;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
    Optional<Recommendation> findByAnalysisId(UUID analysisId);
    Optional<Recommendation> findByIdAndUserId(UUID id, UUID userId);
    Optional<Recommendation> findFirstByUserIdOrderByCreatedAtDescIdDesc(UUID userId);
    Slice<Recommendation> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    @Query("""
            select r from Recommendation r
            where r.userId = :userId
              and (r.createdAt < :createdAt or (r.createdAt = :createdAt and r.id < :id))
            order by r.createdAt desc, r.id desc
            """)
    Slice<Recommendation> findNext(
            @Param("userId") UUID userId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable pageable);
}
