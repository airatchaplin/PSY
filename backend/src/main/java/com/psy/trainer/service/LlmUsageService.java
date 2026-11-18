package com.psy.trainer.service;

import com.psy.trainer.domain.LlmUsage;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.repo.LlmUsageRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LlmUsageService {

    private final LlmUsageRepository repository;
    private final CurrentTherapistService currentTherapistService;

    public LlmUsageService(LlmUsageRepository repository, CurrentTherapistService currentTherapistService) {
        this.repository = repository;
        this.currentTherapistService = currentTherapistService;
    }

    @Transactional
    public void record(
            String model,
            String purpose,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            BigDecimal costRub,
            String generationId
    ) {
        UUID therapistId = null;
        try {
            therapistId = currentTherapistService.findCurrent()
                    .map(TherapistProfile::getId)
                    .orElse(null);
        } catch (Exception ignored) {
            // metering must never break LLM calls
        }
        repository.save(LlmUsage.of(
                therapistId,
                model,
                purpose,
                promptTokens,
                completionTokens,
                totalTokens,
                costRub,
                generationId
        ));
    }

    @Transactional(readOnly = true)
    public Dtos.AdminUsageStatsResponse stats(int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        Instant to = Instant.now();
        Instant from = to.minus(safeDays, ChronoUnit.DAYS);
        return stats(from, to, null, null);
    }

    @Transactional(readOnly = true)
    public Dtos.AdminUsageStatsResponse stats(Instant from, Instant to, String model, String source) {
        Instant safeTo = to == null ? Instant.now() : to;
        Instant safeFrom = from == null ? safeTo.minus(30, ChronoUnit.DAYS) : from;
        if (!safeFrom.isBefore(safeTo)) {
            safeFrom = safeTo.minus(1, ChronoUnit.DAYS);
        }
        // Cap range at 180 days
        if (Duration.between(safeFrom, safeTo).toDays() > 180) {
            safeFrom = safeTo.minus(180, ChronoUnit.DAYS);
        }

        // Empty string (not null) — Hibernate native queries bind null poorly in OR filters.
        String modelFilter = blankToNull(model) == null ? "" : blankToNull(model);
        String sourceFilter = blankToNull(source) == null ? "" : blankToNull(source);
        boolean hourly = Duration.between(safeFrom, safeTo).toHours() <= 48;

        List<Object[]> seriesRows = hourly
                ? repository.aggregateByHourFiltered(safeFrom, safeTo, modelFilter, sourceFilter)
                : repository.aggregateByDayFiltered(safeFrom, safeTo, modelFilter, sourceFilter);

        List<Dtos.UsageByDay> byDay = new ArrayList<>();
        for (Object[] row : seriesRows) {
            byDay.add(new Dtos.UsageByDay(
                    String.valueOf(row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString())
            ));
        }

        List<Dtos.UsageByModel> byModel = new ArrayList<>();
        for (Object[] row : repository.aggregateByModelFiltered(safeFrom, safeTo, modelFilter, sourceFilter)) {
            byModel.add(new Dtos.UsageByModel(
                    String.valueOf(row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString())
            ));
        }

        List<Dtos.UsageByUser> byUser = new ArrayList<>();
        for (Object[] row : repository.aggregateByTherapistFiltered(safeFrom, safeTo, modelFilter, sourceFilter)) {
            UUID therapistId = row[0] == null ? null : UUID.fromString(row[0].toString());
            String name = row[1] == null ? "Неизвестный" : row[1].toString();
            if (therapistId == null) {
                name = "Система / без пользователя";
            }
            String email = row[2] == null ? null : row[2].toString();
            byUser.add(new Dtos.UsageByUser(
                    therapistId,
                    name,
                    email,
                    ((Number) row[3]).longValue(),
                    ((Number) row[4]).longValue(),
                    row[5] == null ? BigDecimal.ZERO : new BigDecimal(row[5].toString())
            ));
        }

        long totalCalls = byModel.stream().mapToLong(Dtos.UsageByModel::calls).sum();
        long totalTokens = byModel.stream().mapToLong(Dtos.UsageByModel::tokens).sum();
        BigDecimal totalCost = byModel.stream()
                .map(Dtos.UsageByModel::costRub)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long modelCount = byModel.size();

        List<String> availableModels = repository.distinctModels(safeFrom, safeTo);
        List<String> availableSources = repository.distinctPurposes(safeFrom, safeTo);

        int daysApprox = (int) Math.max(1, Duration.between(safeFrom, safeTo).toDays());

        return new Dtos.AdminUsageStatsResponse(
                daysApprox,
                safeFrom.toString(),
                safeTo.toString(),
                hourly ? "hour" : "day",
                totalCalls,
                totalTokens,
                totalCost,
                modelCount,
                availableModels,
                availableSources,
                byDay,
                byModel,
                byUser
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
