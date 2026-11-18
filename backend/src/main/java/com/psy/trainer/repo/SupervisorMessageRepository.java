package com.psy.trainer.repo;

import com.psy.trainer.domain.SupervisorMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupervisorMessageRepository extends JpaRepository<SupervisorMessage, UUID> {
    List<SupervisorMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
