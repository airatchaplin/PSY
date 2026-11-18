package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "therapy_session")
public class TherapySession {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "therapist_id", nullable = false)
    private UUID therapistId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "elapsed_seconds", nullable = false)
    private int elapsedSeconds;

    @Column(name = "timer_paused", nullable = false)
    private boolean timerPaused;

    /** When running: wall-clock start of the current running segment. Null while paused/closed. */
    @Column(name = "timer_anchor_at")
    private Instant timerAnchorAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TherapySession start(UUID clientId, UUID therapistId) {
        TherapySession s = new TherapySession();
        s.id = UUID.randomUUID();
        s.clientId = clientId;
        s.therapistId = therapistId;
        s.status = SessionStatus.ACTIVE;
        Instant now = Instant.now();
        s.startedAt = now;
        s.createdAt = now;
        s.elapsedSeconds = 0;
        s.timerPaused = false;
        s.timerAnchorAt = now;
        return s;
    }

    /** Elapsed including current running segment (server clock). */
    public int liveElapsedSeconds() {
        if (timerPaused || timerAnchorAt == null) {
            return Math.max(0, elapsedSeconds);
        }
        long extra = Duration.between(timerAnchorAt, Instant.now()).getSeconds();
        return (int) Math.max(0, elapsedSeconds + Math.max(0, extra));
    }

    public void end() {
        if (status == SessionStatus.CLOSED) {
            return;
        }
        this.elapsedSeconds = liveElapsedSeconds();
        this.status = SessionStatus.CLOSED;
        this.endedAt = Instant.now();
        this.timerPaused = true;
        this.timerAnchorAt = null;
    }

    public void pauseTimer() {
        this.elapsedSeconds = liveElapsedSeconds();
        this.timerPaused = true;
        this.timerAnchorAt = null;
    }

    public void resumeTimer() {
        if (!timerPaused && timerAnchorAt != null) {
            return;
        }
        this.elapsedSeconds = Math.max(0, elapsedSeconds);
        this.timerPaused = false;
        this.timerAnchorAt = Instant.now();
    }

    /** Persist a frozen snapshot without changing pause state (e.g. client sync). */
    public void syncElapsed(int reportedSeconds) {
        int live = liveElapsedSeconds();
        int value = Math.max(0, Math.max(reportedSeconds, live));
        this.elapsedSeconds = value;
        if (!timerPaused) {
            this.timerAnchorAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getTherapistId() {
        return therapistId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    public boolean isTimerPaused() {
        return timerPaused;
    }

    public Instant getTimerAnchorAt() {
        return timerAnchorAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
