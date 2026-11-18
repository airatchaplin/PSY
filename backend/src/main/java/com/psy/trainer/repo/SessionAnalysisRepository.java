package com.psy.trainer.repo;

import com.psy.trainer.domain.AnalysisScope;
import com.psy.trainer.domain.SessionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionAnalysisRepository extends JpaRepository<SessionAnalysis, UUID> {
    List<SessionAnalysis> findByTherapistIdOrderByCreatedAtDesc(UUID therapistId);

    List<SessionAnalysis> findByTherapistIdAndScopeOrderByCreatedAtDesc(UUID therapistId, AnalysisScope scope);

    Optional<SessionAnalysis> findFirstBySessionIdAndScopeOrderByCreatedAtDesc(UUID sessionId, AnalysisScope scope);

    List<SessionAnalysis> findByClientIdAndScopeOrderByCreatedAtDesc(UUID clientId, AnalysisScope scope);
}
