package com.psy.trainer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psy.trainer.domain.AnalysisScope;
import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.MessageRole;
import com.psy.trainer.domain.SessionAnalysis;
import com.psy.trainer.domain.SessionStatus;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.domain.TherapySession;
import com.psy.trainer.llm.LlmCallContext;
import com.psy.trainer.llm.LlmClient;
import com.psy.trainer.llm.LlmUsageContext;
import com.psy.trainer.rag.RagService;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.MessageRepository;
import com.psy.trainer.repo.SessionAnalysisRepository;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class AnalysisService {

    private final CurrentTherapistService currentTherapistService;
    private final TherapySessionRepository sessionRepository;
    private final ClientRepository clientRepository;
    private final MessageRepository messageRepository;
    private final SessionAnalysisRepository analysisRepository;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final RagService ragService;
    private final ObjectMapper objectMapper;
    private final ClientMapper mapper;

    public AnalysisService(
            CurrentTherapistService currentTherapistService,
            TherapySessionRepository sessionRepository,
            ClientRepository clientRepository,
            MessageRepository messageRepository,
            SessionAnalysisRepository analysisRepository,
            LlmClient llmClient,
            PromptLoader promptLoader,
            RagService ragService,
            ObjectMapper objectMapper,
            ClientMapper mapper
    ) {
        this.currentTherapistService = currentTherapistService;
        this.sessionRepository = sessionRepository;
        this.clientRepository = clientRepository;
        this.messageRepository = messageRepository;
        this.analysisRepository = analysisRepository;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Dtos.AnalysisResponse getSessionAnalysis(UUID sessionId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        TherapySession session = sessionRepository.findByIdAndTherapistId(sessionId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Сеанс не найден"));
        clientRepository.findByIdAndTherapistId(session.getClientId(), therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        return analysisRepository
                .findFirstBySessionIdAndScopeOrderByCreatedAtDesc(sessionId, AnalysisScope.SESSION)
                .map(mapper::toAnalysis)
                .orElseThrow(() -> new NotFoundException("Анализ сеанса ещё не создан"));
    }

    @Transactional
    public Dtos.AnalysisResponse analyzeSession(UUID sessionId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        TherapySession session = sessionRepository.findByIdAndTherapistId(sessionId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Сеанс не найден"));
        if (session.getStatus() != SessionStatus.CLOSED) {
            throw new ConflictException("Сначала завершите сеанс кнопкой");
        }
        var existing = analysisRepository
                .findFirstBySessionIdAndScopeOrderByCreatedAtDesc(sessionId, AnalysisScope.SESSION);
        if (existing.isPresent()) {
            return mapper.toAnalysis(existing.get());
        }
        Client client = clientRepository.findByIdAndTherapistId(session.getClientId(), therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return persist(therapist, client.getId(), sessionId, AnalysisScope.SESSION,
                buildTranscript(messages), "Анализ одного сеанса с клиентом " + client.getName());
    }

    @Transactional
    public Dtos.AnalysisResponse analyzeClient(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        Client client = clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        List<Message> messages = messageRepository.findByClientIdOrderByCreatedAtAsc(clientId);
        return persist(therapist, clientId, null, AnalysisScope.CLIENT,
                buildTranscript(messages), "Анализ всех сеансов клиента " + client.getName());
    }

    @Transactional
    public Dtos.AnalysisResponse analyzeGlobal() {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        StringJoiner all = new StringJoiner("\n\n====\n\n");
        for (Client client : clientRepository.findByTherapistIdOrderByUpdatedAtDesc(therapist.getId())) {
            List<Message> messages = messageRepository.findByClientIdOrderByCreatedAtAsc(client.getId());
            all.add("Клиент: " + client.getName() + "\n" + buildTranscript(messages));
        }
        return persist(therapist, null, null, AnalysisScope.GLOBAL,
                all.toString(), "Глобальный анализ практики терапевта");
    }

    @Transactional(readOnly = true)
    public List<Dtos.AnalysisResponse> listGlobalAnalyses() {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        return analysisRepository
                .findByTherapistIdAndScopeOrderByCreatedAtDesc(therapist.getId(), AnalysisScope.GLOBAL)
                .stream()
                .map(mapper::toAnalysis)
                .toList();
    }

    @Transactional(readOnly = true)
    public Dtos.AnalysisResponse getGlobalAnalysis(UUID id) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        return analysisRepository
                .findByIdAndTherapistIdAndScope(id, therapist.getId(), AnalysisScope.GLOBAL)
                .map(mapper::toAnalysis)
                .orElseThrow(() -> new NotFoundException("Анализ тетради не найден"));
    }

    private Dtos.AnalysisResponse persist(
            TherapistProfile therapist,
            UUID clientId,
            UUID sessionId,
            AnalysisScope scope,
            String transcript,
            String task
    ) {
        String purpose = switch (scope) {
            case SESSION -> "analysis_session";
            case CLIENT -> "analysis_client";
            case GLOBAL -> "analysis_global";
        };
        UUID analysisId = UUID.randomUUID();
        LlmCallContext usageCtx = LlmCallContext.of(purpose, clientId, sessionId, analysisId);
        String raw = LlmUsageContext.call(usageCtx, () -> {
            String knowledge = ragService.retrieveGestaltKnowledge(task + "\n" + transcript, 5);
            return llmClient.chatJson(List.of(
                    LlmClient.ChatMessage.system(promptLoader.load("analysis-system.txt")),
                    LlmClient.ChatMessage.user(task + "\n\nМатериалы:\n" + knowledge + "\n\nТранскрипт:\n" + transcript)
            ), usageCtx);
        });
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            String report = node.path("reportMd").asText("Отчёт недоступен");
            String rubric = objectMapper.writeValueAsString(mergeRubric(node));
            String alternatives = objectMapper.writeValueAsString(node.path("alternativeReplies"));
            SessionAnalysis saved = analysisRepository.save(SessionAnalysis.create(
                    analysisId,
                    therapist.getId(),
                    clientId,
                    sessionId,
                    scope,
                    report,
                    rubric,
                    alternatives
            ));
            return mapper.toAnalysis(saved);
        } catch (Exception e) {
            throw new BadRequestException("Не удалось разобрать анализ: " + e.getMessage());
        }
    }

    private JsonNode mergeRubric(JsonNode node) throws Exception {
        var root = objectMapper.createObjectNode();
        root.set("scores", node.path("rubric"));
        root.set("strengths", node.path("strengths"));
        root.set("growthAreas", node.path("growthAreas"));
        root.set("safetyNotes", node.path("safetyNotes"));
        return root;
    }

    private static String buildTranscript(List<Message> messages) {
        StringJoiner joiner = new StringJoiner("\n");
        for (Message m : messages) {
            if (m.getRole() == MessageRole.SYSTEM) {
                joiner.add("--- " + m.getContent() + " ---");
            } else {
                joiner.add(m.getRole() + ": " + m.getContent());
            }
        }
        return joiner.toString();
    }

    private static String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
