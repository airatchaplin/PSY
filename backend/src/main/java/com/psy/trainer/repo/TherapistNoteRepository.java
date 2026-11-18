package com.psy.trainer.repo;

import com.psy.trainer.domain.TherapistNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TherapistNoteRepository extends JpaRepository<TherapistNote, UUID> {
    List<TherapistNote> findByClientIdAndTherapistIdOrderByCreatedAtDesc(UUID clientId, UUID therapistId);

    Optional<TherapistNote> findByIdAndTherapistId(UUID id, UUID therapistId);
}
