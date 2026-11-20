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
@Table(name = "session_analysis")
public class SessionAnalysis {

    @Id
    private UUID id;

    @Column(name = "therapist_id", nullable = false)
    private UUID therapistId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AnalysisScope scope;

    @Column(name = "report_md", nullable = false, columnDefinition = "TEXT")
    private String reportMd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_json", nullable = false, columnDefinition = "jsonb")
    private String rubricJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alternatives_json", columnDefinition = "jsonb")
    private String alternativesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static SessionAnalysis create(
            UUID therapistId,
            UUID clientId,
            UUID sessionId,
            AnalysisScope scope,
            String reportMd,
            String rubricJson,
            String alternativesJson
    ) {
        return create(
                UUID.randomUUID(), therapistId, clientId, sessionId, scope,
                reportMd, rubricJson, alternativesJson
        );
    }

    public static SessionAnalysis create(
            UUID id,
            UUID therapistId,
            UUID clientId,
            UUID sessionId,
            AnalysisScope scope,
            String reportMd,
            String rubricJson,
            String alternativesJson
    ) {
        SessionAnalysis a = new SessionAnalysis();
        a.id = id == null ? UUID.randomUUID() : id;
        a.therapistId = therapistId;
        a.clientId = clientId;
        a.sessionId = sessionId;
        a.scope = scope;
        a.reportMd = reportMd;
        a.rubricJson = rubricJson;
        a.alternativesJson = alternativesJson;
        a.createdAt = Instant.now();
        return a;
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

    public AnalysisScope getScope() {
        return scope;
    }

    public String getReportMd() {
        return reportMd;
    }

    public String getRubricJson() {
        return rubricJson;
    }

    public String getAlternativesJson() {
        return alternativesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
