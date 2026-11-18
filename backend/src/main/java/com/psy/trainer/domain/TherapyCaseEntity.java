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
@Table(name = "therapy_case")
public class TherapyCaseEntity {

    @Id
    private UUID id;

    @Column(length = 128, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(name = "presenting_request", nullable = false, columnDefinition = "text")
    private String presentingRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Difficulty difficulty;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "suggested_tags", columnDefinition = "text[]")
    private String[] suggestedTags;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CaseSource source;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "origin_case_id")
    private UUID originCaseId;

    /** Opaque token for copy-link sharing between therapists. */
    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;

    @Column(name = "prompt_used", columnDefinition = "text")
    private String promptUsed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TherapyCaseEntity create(
            String title,
            String presentingRequest,
            Difficulty difficulty,
            String[] suggestedTags,
            String notes,
            CaseSource source,
            UUID createdBy,
            String promptUsed,
            String slug
    ) {
        return create(title, presentingRequest, difficulty, suggestedTags, notes, source, createdBy, promptUsed, slug, null);
    }

    public static TherapyCaseEntity create(
            String title,
            String presentingRequest,
            Difficulty difficulty,
            String[] suggestedTags,
            String notes,
            CaseSource source,
            UUID createdBy,
            String promptUsed,
            String slug,
            UUID originCaseId
    ) {
        TherapyCaseEntity c = new TherapyCaseEntity();
        Instant now = Instant.now();
        c.id = UUID.randomUUID();
        c.slug = slug;
        c.title = title;
        c.presentingRequest = presentingRequest;
        c.difficulty = difficulty;
        c.suggestedTags = suggestedTags != null ? suggestedTags : new String[0];
        c.notes = notes;
        c.source = source;
        c.createdBy = createdBy;
        c.originCaseId = originCaseId;
        c.promptUsed = promptUsed;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getPresentingRequest() {
        return presentingRequest;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String[] getSuggestedTags() {
        return suggestedTags;
    }

    public String getNotes() {
        return notes;
    }

    public CaseSource getSource() {
        return source;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getOriginCaseId() {
        return originCaseId;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
        this.updatedAt = Instant.now();
    }

    public String getPromptUsed() {
        return promptUsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
