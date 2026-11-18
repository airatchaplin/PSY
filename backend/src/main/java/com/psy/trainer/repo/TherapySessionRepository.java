package com.psy.trainer.repo;

import com.psy.trainer.domain.SessionStatus;
import com.psy.trainer.domain.TherapySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TherapySessionRepository extends JpaRepository<TherapySession, UUID> {
    Optional<TherapySession> findByIdAndTherapistId(UUID id, UUID therapistId);

    Optional<TherapySession> findByClientIdAndStatus(UUID clientId, SessionStatus status);

    List<TherapySession> findByClientIdOrderByStartedAtAsc(UUID clientId);

    long countByClientId(UUID clientId);

    List<TherapySession> findByTherapistIdOrderByStartedAtDesc(UUID therapistId);

    long countByTherapistId(UUID therapistId);

    long countByTherapistIdAndStatus(UUID therapistId, SessionStatus status);
}
