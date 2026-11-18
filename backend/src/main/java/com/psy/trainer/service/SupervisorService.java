package com.psy.trainer.service;

import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.MessageRole;
import com.psy.trainer.domain.SupervisorMessage;
import com.psy.trainer.domain.SupervisorThread;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.llm.LlmClient;
import com.psy.trainer.rag.RagService;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.MessageRepository;
import com.psy.trainer.repo.SupervisorMessageRepository;
import com.psy.trainer.repo.SupervisorThreadRepository;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class SupervisorService {

    private final CurrentTherapistService currentTherapistService;
    private final ClientRepository clientRepository;
    private final TherapySessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final SupervisorThreadRepository threadRepository;
    private final SupervisorMessageRepository supervisorMessageRepository;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final RagService ragService;
    private final ClientMapper mapper;

    public SupervisorService(
            CurrentTherapistService currentTherapistService,
            ClientRepository clientRepository,
            TherapySessionRepository sessionRepository,
            MessageRepository messageRepository,
            SupervisorThreadRepository threadRepository,
            SupervisorMessageRepository supervisorMessageRepository,
            LlmClient llmClient,
            PromptLoader promptLoader,
            RagService ragService,
            ClientMapper mapper
    ) {
        this.currentTherapistService = currentTherapistService;
        this.clientRepository = clientRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.supervisorMessageRepository = supervisorMessageRepository;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.ragService = ragService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Dtos.MessageResponse> history(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        SupervisorThread thread = threadRepository.findByTherapistIdAndClientId(therapist.getId(), clientId)
                .orElse(null);
        if (thread == null) {
            return List.of();
        }
        return supervisorMessageRepository.findByThreadIdOrderByCreatedAtAsc(thread.getId()).stream()
                .map(m -> new Dtos.MessageResponse(
                        m.getId(),
                        thread.getSessionId(),
                        clientId,
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public Dtos.MessageResponse ask(UUID clientId, String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Пустое сообщение");
        }
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        Client client = clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));

        UUID activeSessionId = sessionRepository.findByClientIdAndStatus(
                        clientId, com.psy.trainer.domain.SessionStatus.ACTIVE)
                .map(s -> s.getId())
                .orElse(null);

        SupervisorThread thread = threadRepository.findByTherapistIdAndClientId(therapist.getId(), clientId)
                .orElseGet(() -> threadRepository.save(
                        SupervisorThread.create(therapist.getId(), clientId, activeSessionId)));
        if (activeSessionId != null) {
            thread.setSessionId(activeSessionId);
            threadRepository.save(thread);
        }

        supervisorMessageRepository.save(SupervisorMessage.of(thread.getId(), MessageRole.THERAPIST, content.trim()));

        String knowledge = ragService.retrieveGestaltKnowledge(content, 4);
        List<Message> recent = messageRepository.findTop40ByClientIdOrderByCreatedAtDesc(clientId);
        recent = recent.reversed();

        StringJoiner transcript = new StringJoiner("\n");
        for (Message m : recent) {
            if (m.getRole() == MessageRole.SYSTEM) {
                continue;
            }
            transcript.add(m.getRole() + ": " + m.getContent());
        }

        StringJoiner system = new StringJoiner("\n\n");
        system.add(promptLoader.load("supervisor-system.txt"));
        system.add("Клиент: " + client.getName() + ", " + client.getAge() + " лет");
        system.add("Запрос: " + client.getPresentingRequest());
        system.add("Поведение: " + String.join(", ", client.getBehaviorTags()));
        system.add("Фрагмент транскрипта:\n" + transcript);
        if (!knowledge.isBlank()) {
            system.add("Материалы гештальта:\n" + knowledge);
        }

        List<LlmClient.ChatMessage> messages = new ArrayList<>();
        messages.add(LlmClient.ChatMessage.system(system.toString()));
        for (SupervisorMessage m : supervisorMessageRepository.findByThreadIdOrderByCreatedAtAsc(thread.getId())) {
            if (m.getRole() == MessageRole.THERAPIST) {
                messages.add(LlmClient.ChatMessage.user(m.getContent()));
            } else {
                messages.add(LlmClient.ChatMessage.assistant(m.getContent()));
            }
        }

        String reply = llmClient.chat(messages);
        SupervisorMessage saved = supervisorMessageRepository.save(
                SupervisorMessage.of(thread.getId(), MessageRole.SUPERVISOR, reply));
        return new Dtos.MessageResponse(
                saved.getId(),
                thread.getSessionId(),
                clientId,
                saved.getRole(),
                saved.getContent(),
                saved.getCreatedAt()
        );
    }
}
