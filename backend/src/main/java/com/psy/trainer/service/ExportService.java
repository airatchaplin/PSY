package com.psy.trainer.service;

import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.MessageRole;
import com.psy.trainer.domain.TherapistNote;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.domain.TherapySession;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.MessageRepository;
import com.psy.trainer.repo.TherapistNoteRepository;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExportService {

    private final CurrentTherapistService currentTherapistService;
    private final ClientRepository clientRepository;
    private final TherapySessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final TherapistNoteRepository noteRepository;
    private final LabelMapper labelMapper;

    public ExportService(
            CurrentTherapistService currentTherapistService,
            ClientRepository clientRepository,
            TherapySessionRepository sessionRepository,
            MessageRepository messageRepository,
            TherapistNoteRepository noteRepository,
            LabelMapper labelMapper
    ) {
        this.currentTherapistService = currentTherapistService;
        this.clientRepository = clientRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.noteRepository = noteRepository;
        this.labelMapper = labelMapper;
    }

    @Transactional(readOnly = true)
    public Dtos.ExportResponse exportClient(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        Client client = clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        List<TherapySession> sessions = sessionRepository.findByClientIdOrderByStartedAtAsc(clientId);
        List<Message> messages = messageRepository.findByClientIdOrderByCreatedAtAsc(clientId);
        List<TherapistNote> notes = noteRepository.findByClientIdAndTherapistIdOrderByCreatedAtDesc(clientId, therapist.getId());

        StringBuilder md = new StringBuilder();
        md.append("# Клиент: ").append(client.getName()).append("\n\n");
        md.append("- Возраст: ").append(client.getAge()).append('\n');
        md.append("- Запрос: ").append(client.getPresentingRequest()).append('\n');
        md.append("- Сложность: ").append(labelMapper.difficultyRu(client.getDifficulty())).append('\n');
        md.append("- Поведение: ").append(
                labelMapper.toTags(client.getBehaviorTags()).stream()
                        .map(Dtos.TagDto::labelRu)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("")
        ).append("\n\n");
        md.append("## Сеансы\n");
        for (TherapySession s : sessions) {
            md.append("- ").append(s.getId()).append(" | ").append(s.getStatus())
                    .append(" | ").append(s.getStartedAt());
            if (s.getEndedAt() != null) {
                md.append(" → ").append(s.getEndedAt());
            }
            md.append('\n');
        }
        md.append("\n## Переписка\n\n");
        for (Message m : messages) {
            if (m.getRole() == MessageRole.SYSTEM) {
                md.append("\n--- ").append(m.getContent()).append(" ---\n\n");
            } else {
                md.append("**").append(m.getRole()).append("**: ").append(m.getContent()).append("\n\n");
            }
        }
        if (!notes.isEmpty()) {
            md.append("## Приватные заметки\n\n");
            for (TherapistNote n : notes) {
                md.append("- ").append(n.getCreatedAt()).append(": ").append(n.getContent()).append("\n");
            }
        }
        return new Dtos.ExportResponse(md.toString());
    }
}
