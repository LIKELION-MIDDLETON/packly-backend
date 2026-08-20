package com.jaungangton.api.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {
    Optional<AnalysisJob> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<AnalysisJob> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AnalysisJob job where job.id = :id and job.userId = :userId")
    Optional<AnalysisJob> findForUpdateByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AnalysisJob job where job.id = :id")
    Optional<AnalysisJob> findForUpdateById(@Param("id") UUID id);

    @Query("""
            select job
            from AnalysisJob job
            where job.status = :recommendingStatus
               or (job.status = :analyzingStatus
                   and (job.photoData is not null or job.photoObjectKey is not null))
            order by job.updatedAt asc
            """)
    List<AnalysisJob> findRecoveryCandidates(
            @Param("analyzingStatus") AnalysisStatus analyzingStatus,
            @Param("recommendingStatus") AnalysisStatus recommendingStatus);

    Optional<AnalysisJob> findBySourceResultId(String sourceResultId);
}
