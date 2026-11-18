package com.psy.trainer.web.dto;

import com.psy.trainer.domain.AnalysisScope;
import com.psy.trainer.domain.CaseSource;
import com.psy.trainer.domain.ClientStatus;
import com.psy.trainer.domain.Difficulty;
import com.psy.trainer.domain.MessageRole;
import com.psy.trainer.domain.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {
    }

    public record TagDto(String code, String labelRu) {
    }

    public record MeResponse(
            UUID id,
            String displayName,
            String email,
            boolean disclaimerAccepted,
            Instant disclaimerAcceptedAt,
            boolean admin,
            List<String> roles
    ) {
    }

    /**
     * gender: MALE | FEMALE | ANY | null (null/ANY → random).
     * age: 18..80 or null (null → random in range).
     */
    public record StartReceptionRequest(
            Difficulty difficulty,
            String caseId,
            String gender,
            Integer age
    ) {
    }

    public record ClientResponse(
            UUID id,
            String name,
            int age,
            String gender,
            String presentingRequest,
            Difficulty difficulty,
            String difficultyLabelRu,
            List<TagDto> behaviorTags,
            List<String> defenses,
            String personaJson,
            ClientStatus status,
            String caseId,
            int therapyProgress,
            String coreProblem,
            UUID activeSessionId,
            Integer activeSessionElapsedSeconds,
            Boolean activeSessionTimerPaused,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SessionResponse(
            UUID id,
            UUID clientId,
            SessionStatus status,
            Instant startedAt,
            Instant endedAt,
            int elapsedSeconds,
            boolean timerPaused
    ) {
    }

    public record SessionSummaryResponse(
            UUID id,
            UUID clientId,
            SessionStatus status,
            Instant startedAt,
            Instant endedAt,
            int elapsedSeconds,
            boolean timerPaused,
            int messageCount,
            boolean hasAnalysis,
            String preview
    ) {
    }

    public record TimerUpdateRequest(Integer elapsedSeconds, Boolean paused) {
    }

    public record ReceptionResponse(ClientResponse client, SessionResponse session, String openingLine) {
    }

    public record MessageResponse(
            UUID id,
            UUID sessionId,
            UUID clientId,
            MessageRole role,
            String content,
            Instant createdAt
    ) {
    }

    public record SendMessageRequest(String content) {
    }

    public record NoteRequest(String content, UUID sessionId) {
    }

    public record NoteResponse(
            UUID id,
            UUID clientId,
            UUID sessionId,
            String content,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AnalysisResponse(
            UUID id,
            AnalysisScope scope,
            UUID clientId,
            UUID sessionId,
            String reportMd,
            String rubricJson,
            String alternativesJson,
            Instant createdAt
    ) {
    }

    public record ProgressResponse(
            long clientsTotal,
            long sessionsTotal,
            long sessionsClosed,
            long practiceMinutes,
            List<TagStat> byBehaviorTag,
            List<DifficultyStat> byDifficulty,
            List<RubricStat> rubricAverages,
            List<String> weakZones,
            List<ClientBrief> recentClients
    ) {
    }

    public record TagStat(String tag, String labelRu, long sessions, long minutes, Double avgRubric) {
    }

    public record DifficultyStat(String difficulty, String labelRu, long clients, long sessions) {
    }

    public record RubricStat(String dimension, double average) {
    }

    public record ClientBrief(
            UUID id,
            String name,
            int age,
            List<TagDto> behaviorTags,
            long sessions,
            int therapyProgress
    ) {
    }

    public record CaseResponse(
            UUID id,
            String slug,
            String title,
            String presentingRequest,
            Difficulty difficulty,
            String difficultyLabelRu,
            List<TagDto> suggestedTags,
            String notes,
            CaseSource source,
            Instant createdAt,
            boolean mine,
            boolean builtin,
            boolean canShare,
            String visibilityLabel
    ) {
    }

    public record CreateCaseRequest(
            String title,
            String presentingRequest,
            Difficulty difficulty,
            List<String> suggestedTags,
            String notes
    ) {
    }

    public record GenerateCaseRequest(String prompt) {
    }

    public record TherapistBrief(UUID id, String displayName, String email) {
    }

    public record ShareCaseRequest(List<UUID> therapistIds, boolean all) {
    }

    public record ShareCaseResponse(int sharedCount, List<String> recipients) {
    }

    public record CaseShareLinkResponse(String token, String path) {
    }

    public record ImportSharedCaseResponse(CaseResponse caseItem, boolean alreadyHad) {
    }

    public record ExportResponse(String markdown) {
    }

    public record ErrorBody(String message) {
    }

    public record AdminOverviewResponse(
            BigDecimal balanceRub,
            String keyName,
            BigDecimal keyUsageRub,
            BigDecimal keyLimitRub,
            BigDecimal keyLimitRemainingRub,
            BigDecimal keyUsageMonthlyRub,
            String keyLimitReset,
            boolean keyDisabled,
            String currentModel,
            String defaultModel,
            String baseUrl,
            String defaultBaseUrl,
            String apiKeyMasked,
            boolean apiKeyConfigured,
            String billingError,
            String activityUrl,
            String docsUrl
    ) {
    }

    public record AdminModelOption(String id, String name, Integer contextLength) {
    }

    public record UpdateModelRequest(String model) {
    }

    public record UpdateLlmConnectionRequest(String baseUrl, String apiKey) {
    }

    public record PromptSummary(String id, String label, int length) {
    }

    public record PromptDetail(String id, String label, String content) {
    }

    public record UpdatePromptRequest(String content) {
    }

    public record UsageByDay(String day, long calls, long tokens, BigDecimal costRub) {
    }

    public record UsageByModel(String model, long calls, long tokens, BigDecimal costRub) {
    }

    public record UsageByUser(
            UUID therapistId,
            String displayName,
            String email,
            long calls,
            long tokens,
            BigDecimal costRub
    ) {
    }

    public record AdminUsageStatsResponse(
            int days,
            String from,
            String to,
            String granularity,
            long totalCalls,
            long totalTokens,
            BigDecimal totalCostRub,
            long modelCount,
            List<String> availableModels,
            List<String> availableSources,
            List<UsageByDay> byDay,
            List<UsageByModel> byModel,
            List<UsageByUser> byUser
    ) {
    }

    public record KeycloakUserResponse(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            List<String> roles
    ) {
    }

    public record CreateKeycloakUserRequest(
            String username,
            String password,
            String email,
            String firstName,
            String lastName,
            boolean admin
    ) {
    }
}

