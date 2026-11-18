package com.psy.trainer.repo;

import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByTherapistIdAndStatusOrderByUpdatedAtDesc(UUID therapistId, ClientStatus status);

    List<Client> findByTherapistIdOrderByUpdatedAtDesc(UUID therapistId);

    Optional<Client> findByIdAndTherapistId(UUID id, UUID therapistId);

    long countByTherapistId(UUID therapistId);
}
