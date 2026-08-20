package com.jaungangton.api.engagement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SosReportRepository extends JpaRepository<SosReport, UUID> {
    Optional<SosReport> findByIdAndUserId(UUID id, UUID userId);
    List<SosReport> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
