package com.jaungangton.api.survey;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, UUID> {
    Optional<Survey> findByUserId(UUID userId);
}
