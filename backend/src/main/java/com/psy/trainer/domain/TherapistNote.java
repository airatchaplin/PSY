package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "therapist_note")
public class TherapistNote {

    @Id
    private UUID id;

    @Column(name = "therapist_id", nullable = false)
    private UUID therapistId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TherapistNote create(UUID therapistId, UUID clientId, UUID sessionId, String content) {
        TherapistNote n = new TherapistNote();
        n.id = UUID.randomUUID();
        n.therapistId = therapistId;
        n.clientId = clientId;
        n.sessionId = sessionId;
        n.content = content;
        Instant now = Instant.now();
        n.createdAt = now;
        n.updatedAt = now;
        return n;
    }

    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
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

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
