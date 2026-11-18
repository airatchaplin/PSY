package com.psy.trainer.service;

import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.SessionAnalysis;
import com.psy.trainer.domain.SessionStatus;
import com.psy.trainer.domain.TherapistNote;
import com.psy.trainer.domain.TherapySession;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class ClientMapper {

    private final TherapySessionRepository sessionRepository;
    private final LabelMapper labelMapper;

    public ClientMapper(TherapySessionRepository sessionRepository, LabelMapper labelMapper) {
        this.sessionRepository = sessionRepository;
        this.labelMapper = labelMapper;
    }

    public Dtos.ClientResponse toClient(Client client) {
        var active = sessionRepository.findByClientIdAndStatus(client.getId(), SessionStatus.ACTIVE);
        UUID activeSessionId = active.map(TherapySession::getId).orElse(null);
        Integer elapsed = active.map(TherapySession::liveElapsedSeconds).orElse(null);
        Boolean paused = active.map(TherapySession::isTimerPaused).orElse(null);
        return new Dtos.ClientResponse(
                client.getId(),
                client.getName(),
                client.getAge(),
                client.getGender() == null ? null : client.getGender().name(),
                client.getPresentingRequest(),
                client.getDifficulty(),
                labelMapper.difficultyRu(client.getDifficulty()),
                labelMapper.toTags(client.getBehaviorTags()),
                Arrays.asList(client.getDefenses()),
                client.getPersonaJson(),
                client.getStatus(),
                client.getCaseId(),
                client.getTherapyProgress(),
                client.getCoreProblem(),
                activeSessionId,
                elapsed,
                paused,
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }

    public Dtos.SessionResponse toSession(TherapySession session) {
        return new Dtos.SessionResponse(
                session.getId(),
                session.getClientId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.liveElapsedSeconds(),
                session.isTimerPaused()
        );
    }

    public Dtos.SessionSummaryResponse toSessionSummary(
            TherapySession session,
            int messageCount,
            boolean hasAnalysis,
            String preview
    ) {
        return new Dtos.SessionSummaryResponse(
                session.getId(),
                session.getClientId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.liveElapsedSeconds(),
                session.isTimerPaused(),
                messageCount,
                hasAnalysis,
                preview
        );
    }

    public Dtos.MessageResponse toMessage(Message message) {
        return new Dtos.MessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getClientId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    public Dtos.NoteResponse toNote(TherapistNote note) {
        return new Dtos.NoteResponse(
                note.getId(),
                note.getClientId(),
                note.getSessionId(),
                note.getContent(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    public Dtos.AnalysisResponse toAnalysis(SessionAnalysis analysis) {
        return new Dtos.AnalysisResponse(
                analysis.getId(),
                analysis.getScope(),
                analysis.getClientId(),
                analysis.getSessionId(),
                analysis.getReportMd(),
                analysis.getRubricJson(),
                analysis.getAlternativesJson(),
                analysis.getCreatedAt()
        );
    }

    public List<String> tags(Client client) {
        return Arrays.asList(client.getBehaviorTags());
    }
}
