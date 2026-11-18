package com.psy.trainer.service;

import com.psy.trainer.domain.AnalysisScope;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.MessageRole;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.domain.TherapySession;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.MessageRepository;
import com.psy.trainer.repo.SessionAnalysisRepository;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ClientQueryService {

    private final CurrentTherapistService currentTherapistService;
    private final ClientRepository clientRepository;
    private final TherapySessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final SessionAnalysisRepository analysisRepository;
    private final ClientMapper mapper;

    public ClientQueryService(
            CurrentTherapistService currentTherapistService,
            ClientRepository clientRepository,
            TherapySessionRepository sessionRepository,
            MessageRepository messageRepository,
            SessionAnalysisRepository analysisRepository,
            ClientMapper mapper
    ) {
        this.currentTherapistService = currentTherapistService;
        this.clientRepository = clientRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.analysisRepository = analysisRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Dtos.ClientResponse> list() {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        return clientRepository.findByTherapistIdOrderByUpdatedAtDesc(therapist.getId()).stream()
                .map(mapper::toClient)
                .toList();
    }

    @Transactional(readOnly = true)
    public Dtos.ClientResponse get(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        return clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .map(mapper::toClient)
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
    }

    @Transactional(readOnly = true)
    public List<Dtos.SessionSummaryResponse> sessions(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        return sessionRepository.findByClientIdOrderByStartedAtAsc(clientId).stream()
                .sorted(Comparator.comparing(TherapySession::getStartedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    private Dtos.SessionSummaryResponse toSummary(TherapySession session) {
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        String preview = messages.stream()
                .filter(m -> m.getRole() != MessageRole.SYSTEM)
                .map(Message::getContent)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .map(c -> c.length() > 140 ? c.substring(0, 140) + "…" : c)
                .orElse("");
        boolean hasAnalysis = analysisRepository
                .findFirstBySessionIdAndScopeOrderByCreatedAtDesc(session.getId(), AnalysisScope.SESSION)
                .isPresent();
        return mapper.toSessionSummary(session, messages.size(), hasAnalysis, preview);
    }
}
