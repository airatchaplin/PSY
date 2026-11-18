package com.psy.trainer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psy.trainer.domain.BehaviorTag;
import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.Difficulty;
import com.psy.trainer.domain.SessionAnalysis;
import com.psy.trainer.domain.SessionStatus;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.domain.TherapySession;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.SessionAnalysisRepository;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProgressService {

    private final CurrentTherapistService currentTherapistService;
    private final ClientRepository clientRepository;
    private final TherapySessionRepository sessionRepository;
    private final SessionAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;
    private final LabelMapper labelMapper;

    public ProgressService(
            CurrentTherapistService currentTherapistService,
            ClientRepository clientRepository,
            TherapySessionRepository sessionRepository,
            SessionAnalysisRepository analysisRepository,
            ObjectMapper objectMapper,
            LabelMapper labelMapper
    ) {
        this.currentTherapistService = currentTherapistService;
        this.clientRepository = clientRepository;
        this.sessionRepository = sessionRepository;
        this.analysisRepository = analysisRepository;
        this.objectMapper = objectMapper;
        this.labelMapper = labelMapper;
    }

    @Transactional(readOnly = true)
    public Dtos.ProgressResponse progress() {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        List<Client> clients = clientRepository.findByTherapistIdOrderByUpdatedAtDesc(therapist.getId());
        List<TherapySession> sessions = sessionRepository.findByTherapistIdOrderByStartedAtDesc(therapist.getId());
        List<SessionAnalysis> analyses = analysisRepository.findByTherapistIdOrderByCreatedAtDesc(therapist.getId());

        long closed = sessions.stream().filter(s -> s.getStatus() == SessionStatus.CLOSED).count();
        long minutes = sessions.stream().mapToLong(this::minutesOf).sum();

        Map<String, Agg> byTag = new HashMap<>();
        Map<Difficulty, long[]> byDiff = new EnumMap<>(Difficulty.class);
        for (Difficulty d : Difficulty.values()) {
            byDiff.put(d, new long[]{0, 0});
        }

        Map<UUID, List<TherapySession>> sessionsByClient = new HashMap<>();
        for (TherapySession s : sessions) {
            sessionsByClient.computeIfAbsent(s.getClientId(), k -> new ArrayList<>()).add(s);
        }

        for (Client client : clients) {
            long[] diff = byDiff.get(client.getDifficulty());
            diff[0]++;
            List<TherapySession> clientSessions = sessionsByClient.getOrDefault(client.getId(), List.of());
            diff[1] += clientSessions.size();
            long clientMinutes = clientSessions.stream().mapToLong(this::minutesOf).sum();
            Double avg = averageRubricForClient(client.getId(), analyses);
            for (String tag : client.getBehaviorTags()) {
                Agg agg = byTag.computeIfAbsent(tag, t -> new Agg());
                agg.sessions += clientSessions.size();
                agg.minutes += clientMinutes;
                if (avg != null) {
                    agg.rubricSum += avg;
                    agg.rubricCount++;
                }
            }
        }

        List<Dtos.TagStat> tagStats = byTag.entrySet().stream()
                .map(e -> new Dtos.TagStat(
                        e.getKey(),
                        labelOf(e.getKey()),
                        e.getValue().sessions,
                        e.getValue().minutes,
                        e.getValue().rubricCount == 0 ? null : e.getValue().rubricSum / e.getValue().rubricCount
                ))
                .sorted(Comparator.comparingLong(Dtos.TagStat::sessions).reversed())
                .toList();

        List<Dtos.DifficultyStat> difficultyStats = byDiff.entrySet().stream()
                .map(e -> new Dtos.DifficultyStat(
                        e.getKey().name(),
                        e.getKey().getLabelRu(),
                        e.getValue()[0],
                        e.getValue()[1]
                ))
                .toList();

        Map<String, AvgAcc> rubric = new HashMap<>();
        for (SessionAnalysis a : analyses) {
            collectRubric(a.getRubricJson(), rubric);
        }
        List<Dtos.RubricStat> rubricStats = rubric.entrySet().stream()
                .map(e -> new Dtos.RubricStat(e.getKey(), e.getValue().sum / e.getValue().count))
                .sorted(Comparator.comparingDouble(Dtos.RubricStat::average))
                .toList();

        List<String> weak = rubricStats.stream().limit(3).map(Dtos.RubricStat::dimension).toList();

        List<Dtos.ClientBrief> recent = clients.stream().limit(8)
                .map(c -> new Dtos.ClientBrief(
                        c.getId(),
                        c.getName(),
                        c.getAge(),
                        labelMapper.toTags(c.getBehaviorTags()),
                        sessionsByClient.getOrDefault(c.getId(), List.of()).size(),
                        c.getTherapyProgress()
                ))
                .toList();

        return new Dtos.ProgressResponse(
                clients.size(),
                sessions.size(),
                closed,
                minutes,
                tagStats,
                difficultyStats,
                rubricStats,
                weak,
                recent
        );
    }

    private Double averageRubricForClient(UUID clientId, List<SessionAnalysis> analyses) {
        Map<String, AvgAcc> map = new HashMap<>();
        for (SessionAnalysis a : analyses) {
            if (clientId.equals(a.getClientId())) {
                collectRubric(a.getRubricJson(), map);
            }
        }
        if (map.isEmpty()) {
            return null;
        }
        double sum = 0;
        int count = 0;
        for (AvgAcc v : map.values()) {
            sum += v.sum / v.count;
            count++;
        }
        return count == 0 ? null : sum / count;
    }

    private void collectRubric(String rubricJson, Map<String, AvgAcc> into) {
        if (rubricJson == null) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rubricJson);
            JsonNode scores = root.has("scores") ? root.get("scores") : root;
            scores.fields().forEachRemaining(e -> {
                if (e.getValue().isNumber()) {
                    AvgAcc acc = into.computeIfAbsent(e.getKey(), k -> new AvgAcc());
                    acc.sum += e.getValue().asDouble();
                    acc.count++;
                }
            });
        } catch (Exception ignored) {
            // ignore malformed
        }
    }

    private long minutesOf(TherapySession session) {
        Instant end = session.getEndedAt() != null ? session.getEndedAt() : Instant.now();
        return Math.max(1, Duration.between(session.getStartedAt(), end).toMinutes());
    }

    private static String labelOf(String tag) {
        try {
            return BehaviorTag.valueOf(tag).getLabelRu();
        } catch (Exception e) {
            return tag;
        }
    }

    private static class Agg {
        long sessions;
        long minutes;
        double rubricSum;
        int rubricCount;
    }

    private static class AvgAcc {
        double sum;
        int count;
    }
}
