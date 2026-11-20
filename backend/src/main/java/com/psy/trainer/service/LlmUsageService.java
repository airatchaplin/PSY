package com.psy.trainer.service;

import com.psy.trainer.domain.LlmUsage;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.llm.RouterAiClient;
import com.psy.trainer.repo.LlmUsageRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LlmUsageService {

    private final LlmUsageRepository repository;
    private final CurrentTherapistService currentTherapistService;
    private final UserBalanceService userBalanceService;
    private final RouterAiClient routerAiClient;

    public LlmUsageService(
            LlmUsageRepository repository,
            CurrentTherapistService currentTherapistService,
            UserBalanceService userBalanceService,
            RouterAiClient routerAiClient
    ) {
        this.repository = repository;
        this.currentTherapistService = currentTherapistService;
        this.userBalanceService = userBalanceService;
        this.routerAiClient = routerAiClient;
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
        record(model, purpose, promptTokens, completionTokens, totalTokens, costRub, generationId, null, null, null);
    }

    @Transactional
    public void record(
            String model,
            String purpose,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            BigDecimal costRub,
            String generationId,
            UUID clientId,
            UUID sessionId
    ) {
        record(model, purpose, promptTokens, completionTokens, totalTokens, costRub, generationId, clientId, sessionId, null);
    }

    @Transactional
    public void record(
            String model,
            String purpose,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            BigDecimal costRub,
            String generationId,
            UUID clientId,
            UUID sessionId,
            UUID refId
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
                clientId,
                sessionId,
                refId,
                model,
                purpose,
                promptTokens,
                completionTokens,
                totalTokens,
                costRub,
                generationId
        ));
        try {
            userBalanceService.chargeCurrentUser(costRub);
        } catch (Exception ignored) {
            // balance charge must not break LLM calls
        }
    }

    @Transactional
    public Dtos.AdminUsageStatsResponse stats(int days) {
        int safeDays = Math.max(1, Math.min(days, 90));
        Instant to = Instant.now();
        Instant from = to.minus(safeDays, ChronoUnit.DAYS);
        return stats(from, to, null, null);
    }

    /** Fill missing costs from model catalog prices × tokens (best-effort). */
    @Transactional
    public void backfillMissingCosts() {
        List<LlmUsage> rows = repository.findMissingCostWithTokens();
        if (rows.isEmpty()) {
            return;
        }
        for (LlmUsage row : rows) {
            BigDecimal estimated = estimateCost(row.getModel(), row.getPromptTokens(), row.getCompletionTokens(), row.getTotalTokens());
            if (estimated != null && estimated.compareTo(BigDecimal.ZERO) > 0) {
                row.setCostRub(estimated);
            }
        }
        repository.saveAll(rows);
    }

    private BigDecimal estimateCost(String model, Integer prompt, Integer completion, Integer total) {
        try {
            BigDecimal promptPrice = routerAiClient.promptPricePerToken(model);
            if (promptPrice == null) {
                return null;
            }
            int promptTokens = prompt != null ? prompt : (total != null ? total : 0);
            int completionTokens = completion != null ? completion : 0;
            if (promptTokens <= 0 && completionTokens <= 0) {
                return null;
            }
            BigDecimal cost = promptPrice.multiply(BigDecimal.valueOf(Math.max(promptTokens, 0)));
            if (completionTokens > 0) {
                cost = cost.add(promptPrice.multiply(BigDecimal.valueOf(completionTokens)));
            }
            return cost.setScale(6, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional
    public Dtos.AdminUsageStatsResponse stats(Instant from, Instant to, String model, String source) {
        backfillMissingCostsBestEffort();
        Instant[] range = normalizeRange(from, to);
        Instant safeFrom = range[0];
        Instant safeTo = range[1];
        String modelFilter = blankToEmpty(model);
        String sourceFilter = blankToEmpty(source);
        boolean hourly = Duration.between(safeFrom, safeTo).toHours() <= 48;

        List<Object[]> seriesRows = hourly
                ? repository.aggregateByHourFiltered(safeFrom, safeTo, modelFilter, sourceFilter)
                : repository.aggregateByDayFiltered(safeFrom, safeTo, modelFilter, sourceFilter);

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
                    toMoney(row[5])
            ));
        }

        return buildResponse(
                safeFrom,
                safeTo,
                hourly,
                mapByDay(seriesRows),
                mapByModel(repository.aggregateByModelFiltered(safeFrom, safeTo, modelFilter, sourceFilter)),
                byUser,
                mapBySource(repository.aggregateByPurposeFiltered(safeFrom, safeTo, modelFilter, sourceFilter)),
                foldByClient(repository.aggregateByClientPurposeFiltered(safeFrom, safeTo, modelFilter, sourceFilter)),
                foldGeneral(repository.aggregateGeneralByRefFiltered(safeFrom, safeTo, modelFilter, sourceFilter)),
                repository.distinctModels(safeFrom, safeTo),
                repository.distinctPurposes(safeFrom, safeTo)
        );
    }

    @Transactional
    public Dtos.AdminUsageStatsResponse statsForTherapist(
            UUID therapistId,
            Instant from,
            Instant to,
            String model,
            String source
    ) {
        if (therapistId == null) {
            throw new BadRequestException("Не указан терапевт");
        }
        backfillMissingCostsBestEffort();
        Instant[] range = normalizeRange(from, to);
        Instant safeFrom = range[0];
        Instant safeTo = range[1];
        String modelFilter = blankToEmpty(model);
        String sourceFilter = blankToEmpty(source);
        boolean hourly = Duration.between(safeFrom, safeTo).toHours() <= 48;

        List<Object[]> seriesRows = hourly
                ? repository.aggregateByHourForTherapist(therapistId, safeFrom, safeTo, modelFilter, sourceFilter)
                : repository.aggregateByDayForTherapist(therapistId, safeFrom, safeTo, modelFilter, sourceFilter);

        return buildResponse(
                safeFrom,
                safeTo,
                hourly,
                mapByDay(seriesRows),
                mapByModel(repository.aggregateByModelForTherapist(
                        therapistId, safeFrom, safeTo, modelFilter, sourceFilter
                )),
                List.of(),
                mapBySource(repository.aggregateByPurposeForTherapist(
                        therapistId, safeFrom, safeTo, modelFilter, sourceFilter
                )),
                foldByClient(repository.aggregateByClientPurposeForTherapist(
                        therapistId, safeFrom, safeTo, modelFilter, sourceFilter
                )),
                foldGeneral(repository.aggregateGeneralByRefForTherapist(
                        therapistId, safeFrom, safeTo, modelFilter, sourceFilter
                )),
                repository.distinctModelsForTherapist(therapistId, safeFrom, safeTo),
                repository.distinctPurposesForTherapist(therapistId, safeFrom, safeTo)
        );
    }

    private void backfillMissingCostsBestEffort() {
        try {
            backfillMissingCosts();
        } catch (Exception ignored) {
            // stats must still load
        }
    }

    private static Instant[] normalizeRange(Instant from, Instant to) {
        Instant safeTo = to == null ? Instant.now() : to;
        Instant safeFrom = from == null ? safeTo.minus(30, ChronoUnit.DAYS) : from;
        if (!safeFrom.isBefore(safeTo)) {
            safeFrom = safeTo.minus(1, ChronoUnit.DAYS);
        }
        if (Duration.between(safeFrom, safeTo).toDays() > 180) {
            safeFrom = safeTo.minus(180, ChronoUnit.DAYS);
        }
        return new Instant[]{safeFrom, safeTo};
    }

    private static List<Dtos.UsageByDay> mapByDay(List<Object[]> rows) {
        List<Dtos.UsageByDay> byDay = new ArrayList<>();
        for (Object[] row : rows) {
            byDay.add(new Dtos.UsageByDay(
                    String.valueOf(row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    toMoney(row[3])
            ));
        }
        return byDay;
    }

    private static List<Dtos.UsageByModel> mapByModel(List<Object[]> rows) {
        List<Dtos.UsageByModel> byModel = new ArrayList<>();
        for (Object[] row : rows) {
            byModel.add(new Dtos.UsageByModel(
                    String.valueOf(row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    toMoney(row[3])
            ));
        }
        return byModel;
    }

    private static List<Dtos.UsageBySource> mapBySource(List<Object[]> rows) {
        List<Dtos.UsageBySource> bySource = new ArrayList<>();
        for (Object[] row : rows) {
            bySource.add(new Dtos.UsageBySource(
                    String.valueOf(row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    toMoney(row[3])
            ));
        }
        return bySource;
    }

    private static List<Dtos.UsageByClient> foldByClient(List<Object[]> rows) {
        record ClientKey(UUID clientId, String name, Integer age) {
        }
        record SessionKey(UUID sessionId, Instant startedAt, String status, boolean hasAnalysis) {
        }
        record PurposeAgg(long calls, long tokens, BigDecimal cost) {
            PurposeAgg add(long c, long t, BigDecimal costRub) {
                return new PurposeAgg(calls + c, tokens + t, cost.add(costRub));
            }
        }

        Map<ClientKey, Map<SessionKey, Map<String, PurposeAgg>>> tree = new LinkedHashMap<>();

        for (Object[] row : rows) {
            UUID clientId = row[0] == null ? null : UUID.fromString(row[0].toString());
            String name = clientId == null
                    ? "Общее"
                    : (row[1] == null || row[1].toString().isBlank() ? "Клиент" : row[1].toString());
            Integer age = row[2] == null ? null : ((Number) row[2]).intValue();
            UUID sessionId = row[3] == null ? null : UUID.fromString(row[3].toString());
            Instant startedAt = toInstant(row[4]);
            String status = row[5] == null ? null : row[5].toString();
            boolean hasAnalysis = toBoolean(row[6]);
            String purpose = String.valueOf(row[7]);
            long calls = ((Number) row[8]).longValue();
            long tokens = ((Number) row[9]).longValue();
            BigDecimal cost = toMoney(row[10]);

            if (clientId == null) {
                continue;
            }
            ClientKey clientKey = new ClientKey(clientId, name, age);
            SessionKey sessionKey = new SessionKey(sessionId, startedAt, status, hasAnalysis);
            tree.computeIfAbsent(clientKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(sessionKey, k -> new LinkedHashMap<>())
                    .merge(purpose, new PurposeAgg(calls, tokens, cost), (a, b) -> a.add(b.calls, b.tokens, b.cost));
        }

        List<Dtos.UsageByClient> result = new ArrayList<>();
        for (Map.Entry<ClientKey, Map<SessionKey, Map<String, PurposeAgg>>> clientEntry : tree.entrySet()) {
            ClientKey clientKey = clientEntry.getKey();
            Map<String, PurposeAgg> clientPurposes = new LinkedHashMap<>();
            long clientCalls = 0;
            long clientTokens = 0;
            BigDecimal clientCost = BigDecimal.ZERO;

            List<Map.Entry<SessionKey, Map<String, PurposeAgg>>> sessionEntries =
                    new ArrayList<>(clientEntry.getValue().entrySet());

            // №1 = oldest real session (stable numbering independent of display order).
            List<SessionKey> numbered = sessionEntries.stream()
                    .map(Map.Entry::getKey)
                    .filter(k -> k.sessionId() != null)
                    .sorted((a, b) -> {
                        Instant sa = a.startedAt();
                        Instant sb = b.startedAt();
                        if (sa == null && sb == null) return 0;
                        if (sa == null) return 1;
                        if (sb == null) return -1;
                        return sa.compareTo(sb);
                    })
                    .toList();
            Map<SessionKey, Integer> sessionNumbers = new HashMap<>();
            for (int i = 0; i < numbered.size(); i++) {
                sessionNumbers.put(numbered.get(i), i + 1);
            }

            // Display: «Общее» first, then sessions newest → oldest.
            sessionEntries.sort((a, b) -> {
                UUID idA = a.getKey().sessionId();
                UUID idB = b.getKey().sessionId();
                if (idA == null && idB == null) return 0;
                if (idA == null) return -1;
                if (idB == null) return 1;
                Instant sa = a.getKey().startedAt();
                Instant sb = b.getKey().startedAt();
                if (sa == null && sb == null) return 0;
                if (sa == null) return 1;
                if (sb == null) return -1;
                return sb.compareTo(sa);
            });

            List<Dtos.UsageBySession> sessions = new ArrayList<>();
            for (Map.Entry<SessionKey, Map<String, PurposeAgg>> sessionEntry : sessionEntries) {
                SessionKey sessionKey = sessionEntry.getKey();
                List<Dtos.UsageBySource> purposeRows = new ArrayList<>();
                long sessionCalls = 0;
                long sessionTokens = 0;
                BigDecimal sessionCost = BigDecimal.ZERO;
                for (Map.Entry<String, PurposeAgg> pe : sessionEntry.getValue().entrySet()) {
                    PurposeAgg agg = pe.getValue();
                    purposeRows.add(new Dtos.UsageBySource(pe.getKey(), agg.calls(), agg.tokens(), agg.cost()));
                    sessionCalls += agg.calls();
                    sessionTokens += agg.tokens();
                    sessionCost = sessionCost.add(agg.cost());
                    clientPurposes.merge(pe.getKey(), agg, (a, b) -> a.add(b.calls(), b.tokens(), b.cost()));
                }
                purposeRows.sort((a, b) -> b.costRub().compareTo(a.costRub()));
                clientCalls += sessionCalls;
                clientTokens += sessionTokens;
                clientCost = clientCost.add(sessionCost);

                String label;
                if (sessionKey.sessionId() == null) {
                    label = "Общее";
                } else {
                    int sessionNo = sessionNumbers.getOrDefault(sessionKey, 0);
                    label = "Сеанс №" + sessionNo + (sessionKey.startedAt() == null
                            ? ""
                            : " · " + formatSessionWhen(sessionKey.startedAt()));
                }
                sessions.add(new Dtos.UsageBySession(
                        sessionKey.sessionId(),
                        label,
                        sessionKey.startedAt(),
                        sessionKey.status(),
                        sessionKey.hasAnalysis(),
                        sessionCalls,
                        sessionTokens,
                        sessionCost,
                        purposeRows
                ));
            }

            List<Dtos.UsageBySource> byPurpose = new ArrayList<>();
            for (Map.Entry<String, PurposeAgg> pe : clientPurposes.entrySet()) {
                PurposeAgg agg = pe.getValue();
                byPurpose.add(new Dtos.UsageBySource(pe.getKey(), agg.calls(), agg.tokens(), agg.cost()));
            }
            byPurpose.sort((a, b) -> b.costRub().compareTo(a.costRub()));

            result.add(new Dtos.UsageByClient(
                    clientKey.clientId(),
                    clientKey.name(),
                    clientKey.age(),
                    clientCalls,
                    clientTokens,
                    clientCost,
                    byPurpose,
                    sessions
            ));
        }
        // «Общее» (no client) first, then by cost desc.
        result.sort((a, b) -> {
            boolean aGeneral = a.clientId() == null;
            boolean bGeneral = b.clientId() == null;
            if (aGeneral != bGeneral) {
                return aGeneral ? -1 : 1;
            }
            return b.costRub().compareTo(a.costRub());
        });
        return result;
    }

    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatSessionWhen(Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                .withLocale(java.util.Locale.forLanguageTag("ru"))
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant);
    }

    private static List<Dtos.UsageByGeneral> foldGeneral(List<Object[]> rows) {
        record Agg(long calls, long tokens, BigDecimal cost) {
            Agg add(long c, long t, BigDecimal costRub) {
                return new Agg(calls + c, tokens + t, cost.add(costRub));
            }
        }
        Map<String, Agg> totals = new LinkedHashMap<>();
        Map<String, List<Dtos.UsageByGeneralItem>> items = new LinkedHashMap<>();
        for (String purpose : List.of("analysis_global", "case", "embed")) {
            totals.put(purpose, new Agg(0, 0, BigDecimal.ZERO));
            items.put(purpose, new ArrayList<>());
        }

        for (Object[] row : rows) {
            String purpose = String.valueOf(row[0]);
            if (!totals.containsKey(purpose)) {
                continue;
            }
            UUID refId = row[1] == null ? null : UUID.fromString(row[1].toString());
            String label = row[2] == null ? purpose : row[2].toString();
            Instant createdAt = toInstant(row[3]);
            long calls = ((Number) row[4]).longValue();
            long tokens = ((Number) row[5]).longValue();
            BigDecimal cost = toMoney(row[6]);
            totals.put(purpose, totals.get(purpose).add(calls, tokens, cost));

            if (refId == null || "embed".equals(purpose)) {
                continue;
            }
            if ("analysis_global".equals(purpose)) {
                label = createdAt == null
                        ? "Анализ тетради"
                        : "Анализ · " + formatSessionWhen(createdAt);
            }
            items.get(purpose).add(new Dtos.UsageByGeneralItem(refId, label, createdAt, calls, tokens, cost));
        }

        List<Dtos.UsageByGeneral> out = new ArrayList<>();
        for (String purpose : List.of("analysis_global", "case", "embed")) {
            Agg agg = totals.get(purpose);
            if (agg.calls() <= 0 && agg.cost().signum() == 0) {
                continue;
            }
            List<Dtos.UsageByGeneralItem> purposeItems = items.get(purpose);
            purposeItems.sort((a, b) -> {
                Instant ca = a.createdAt();
                Instant cb = b.createdAt();
                if (ca == null && cb == null) return 0;
                if (ca == null) return 1;
                if (cb == null) return -1;
                return cb.compareTo(ca);
            });
            out.add(new Dtos.UsageByGeneral(purpose, agg.calls(), agg.tokens(), agg.cost(), purposeItems));
        }
        return out;
    }

    private static Dtos.AdminUsageStatsResponse buildResponse(
            Instant safeFrom,
            Instant safeTo,
            boolean hourly,
            List<Dtos.UsageByDay> byDay,
            List<Dtos.UsageByModel> byModel,
            List<Dtos.UsageByUser> byUser,
            List<Dtos.UsageBySource> bySource,
            List<Dtos.UsageByClient> byClient,
            List<Dtos.UsageByGeneral> byGeneral,
            List<String> availableModels,
            List<String> availableSources
    ) {
        long totalCalls = byModel.stream().mapToLong(Dtos.UsageByModel::calls).sum();
        long totalTokens = byModel.stream().mapToLong(Dtos.UsageByModel::tokens).sum();
        BigDecimal totalCost = byModel.stream()
                .map(Dtos.UsageByModel::costRub)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long modelCount = byModel.size();
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
                byUser,
                bySource,
                byClient,
                byGeneral
        );
    }

    private static BigDecimal toMoney(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static String blankToEmpty(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim();
    }
}
