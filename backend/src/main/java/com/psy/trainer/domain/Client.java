package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client")
public class Client {

    @Id
    private UUID id;

    @Column(name = "therapist_id", nullable = false)
    private UUID therapistId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ClientGender gender;

    @Column(name = "presenting_request", nullable = false, columnDefinition = "TEXT")
    private String presentingRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Difficulty difficulty;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "behavior_tags", columnDefinition = "text[]")
    private String[] behaviorTags;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] defenses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "persona_json", nullable = false, columnDefinition = "jsonb")
    private String personaJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClientStatus status;

    @Column(name = "case_id", length = 64)
    private String caseId;

    @Column(name = "therapy_progress", nullable = false)
    private int therapyProgress;

    @Column(name = "core_problem", columnDefinition = "TEXT")
    private String coreProblem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Client create(
            UUID therapistId,
            String name,
            int age,
            ClientGender gender,
            String presentingRequest,
            Difficulty difficulty,
            String[] behaviorTags,
            String[] defenses,
            String personaJson,
            String caseId,
            String coreProblem
    ) {
        Client c = new Client();
        c.id = UUID.randomUUID();
        c.therapistId = therapistId;
        c.name = name;
        c.age = age;
        c.gender = gender;
        c.presentingRequest = presentingRequest;
        c.difficulty = difficulty;
        c.behaviorTags = behaviorTags;
        c.defenses = defenses;
        c.personaJson = personaJson;
        c.status = ClientStatus.ACTIVE;
        c.caseId = caseId;
        c.therapyProgress = 0;
        c.coreProblem = coreProblem != null && !coreProblem.isBlank() ? coreProblem : presentingRequest;
        Instant now = Instant.now();
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    public void updateTherapyState(int progress, String coreProblem, boolean completed) {
        this.therapyProgress = Math.max(0, Math.min(100, progress));
        if (coreProblem != null && !coreProblem.isBlank()) {
            this.coreProblem = coreProblem;
        }
        if (completed) {
            this.status = ClientStatus.COMPLETED;
        }
        this.updatedAt = Instant.now();
    }

    /** Fill fields after LLM persona generation (client row already exists for usage attribution). */
    public void applyGeneratedPersona(
            String name,
            int age,
            ClientGender gender,
            String presentingRequest,
            Difficulty difficulty,
            String[] behaviorTags,
            String[] defenses,
            String personaJson,
            String coreProblem
    ) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.presentingRequest = presentingRequest;
        this.difficulty = difficulty;
        this.behaviorTags = behaviorTags;
        this.defenses = defenses;
        this.personaJson = personaJson;
        this.coreProblem = coreProblem != null && !coreProblem.isBlank() ? coreProblem : presentingRequest;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTherapistId() {
        return therapistId;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public ClientGender getGender() {
        return gender;
    }

    public String getPresentingRequest() {
        return presentingRequest;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String[] getBehaviorTags() {
        return behaviorTags;
    }

    public String[] getDefenses() {
        return defenses;
    }

    public String getPersonaJson() {
        return personaJson;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getCaseId() {
        return caseId;
    }

    public int getTherapyProgress() {
        return therapyProgress;
    }

    public String getCoreProblem() {
        return coreProblem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
