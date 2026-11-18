package com.psy.trainer.repo;

import com.psy.trainer.domain.SupervisorThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupervisorThreadRepository extends JpaRepository<SupervisorThread, UUID> {
    Optional<SupervisorThread> findByTherapistIdAndClientId(UUID therapistId, UUID clientId);
}
