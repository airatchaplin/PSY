package com.psy.trainer.repo;

import com.psy.trainer.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByClientIdOrderByCreatedAtAsc(UUID clientId);

    List<Message> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<Message> findTop40ByClientIdOrderByCreatedAtDesc(UUID clientId);

    long countBySessionId(UUID sessionId);
}
