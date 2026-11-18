package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_usage")
public class LlmUsage {

    @Id
    private UUID id;

    @Column(name = "therapist_id")
    private UUID therapistId;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false, length = 64)
    private String purpose = "chat";

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "cost_rub", precision = 14, scale = 6)
    private BigDecimal costRub;

    @Column(name = "generation_id", length = 128)
    private String generationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LlmUsage() {
    }

    public static LlmUsage of(
            UUID therapistId,
            String model,
            String purpose,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            BigDecimal costRub,
            String generationId
    ) {
        LlmUsage u = new LlmUsage();
        u.id = UUID.randomUUID();
        u.therapistId = therapistId;
        u.model = model;
        u.purpose = purpose == null || purpose.isBlank() ? "chat" : purpose;
        u.promptTokens = promptTokens;
        u.completionTokens = completionTokens;
        u.totalTokens = totalTokens;
        u.costRub = costRub;
        u.generationId = generationId;
        u.createdAt = Instant.now();
        return u;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTherapistId() {
        return therapistId;
    }

    public String getModel() {
        return model;
    }

    public String getPurpose() {
        return purpose;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public BigDecimal getCostRub() {
        return costRub;
    }

    public String getGenerationId() {
        return generationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
