package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supervisor_thread")
public class SupervisorThread {

    @Id
    private UUID id;

    @Column(name = "therapist_id", nullable = false)
    private UUID therapistId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static SupervisorThread create(UUID therapistId, UUID clientId, UUID sessionId) {
        SupervisorThread t = new SupervisorThread();
        t.id = UUID.randomUUID();
        t.therapistId = therapistId;
        t.clientId = clientId;
        t.sessionId = sessionId;
        t.createdAt = Instant.now();
        return t;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTherapistId() {
        return therapistId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
