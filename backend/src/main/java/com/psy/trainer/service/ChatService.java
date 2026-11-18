package com.psy.trainer.service;

import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.ClientStatus;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.MessageRole;
import com.psy.trainer.domain.SessionStatus;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.domain.TherapySession;
import com.psy.trainer.llm.LlmClient;
import com.psy.trainer.rag.RagService;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.MessageRepository;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class ChatService {

    private final CurrentTherapistService currentTherapistService;
    private final TherapySessionRepository sessionRepository;
    private final ClientRepository clientRepository;
    private final MessageRepository messageRepository;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final RagService ragService;
    private final ClientMapper mapper;

    public ChatService(
            CurrentTherapistService currentTherapistService,
            TherapySessionRepository sessionRepository,
            ClientRepository clientRepository,
            MessageRepository messageRepository,
            LlmClient llmClient,
            PromptLoader promptLoader,
            RagService ragService,
            ClientMapper mapper
    ) {
        this.currentTherapistService = currentTherapistService;
        this.sessionRepository = sessionRepository;
        this.clientRepository = clientRepository;
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.ragService = ragService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Dtos.MessageResponse> history(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        return messageRepository.findByClientIdOrderByCreatedAtAsc(clientId).stream()
                .map(mapper::toMessage)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.MessageResponse> sessionHistory(UUID sessionId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        TherapySession session = sessionRepository.findByIdAndTherapistId(sessionId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Сеанс не найден"));
        clientRepository.findByIdAndTherapistId(session.getClientId(), therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(mapper::toMessage)
                .toList();
    }

    @Transactional
    public Dtos.MessageResponse send(UUID sessionId, String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Пустое сообщение");
        }
        Context ctx = loadActive(sessionId);
        return exchange(ctx, content.trim());
    }

    /**
     * Send therapist message; if there is no ACTIVE session, start one with therapist as initiator
     * (no client opening line).
     */
    @Transactional
    public Dtos.MessageResponse sendToClient(UUID clientId, String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Пустое сообщение");
        }
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        Client client = clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        if (client.getStatus() == ClientStatus.COMPLETED) {
            throw new ConflictException("Клиент завершил терапию — новый сеанс не нужен");
        }
        TherapySession session = sessionRepository.findByClientIdAndStatus(clientId, SessionStatus.ACTIVE)
                .orElseGet(() -> startTherapistInitiated(client, therapist));
        if (session.isTimerPaused()) {
            throw new ConflictException("Снимите паузу кнопкой «Начать сеанс», чтобы продолжить разговор");
        }
        return exchange(new Context(therapist, client, session), content.trim());
    }

    private TherapySession startTherapistInitiated(Client client, TherapistProfile therapist) {
        TherapySession session = sessionRepository.save(TherapySession.start(client.getId(), therapist.getId()));
        long sessionNo = sessionRepository.countByClientId(client.getId());
        messageRepository.save(Message.of(
                session.getId(),
                client.getId(),
                MessageRole.SYSTEM,
                "Начат сеанс №" + sessionNo
        ));
        return session;
    }

    private Dtos.MessageResponse exchange(Context ctx, String therapistText) {
        Message therapistMsg = messageRepository.save(Message.of(
                ctx.session().getId(), ctx.client().getId(), MessageRole.THERAPIST, therapistText));
        ragService.indexMessage(therapistMsg, ctx.therapist().getId());

        String reply = llmClient.chat(buildClientMessages(ctx.client(), therapistText));
        Message clientMsg = messageRepository.save(Message.of(
                ctx.session().getId(), ctx.client().getId(), MessageRole.CLIENT, reply));
        ragService.indexMessage(clientMsg, ctx.therapist().getId());
        return mapper.toMessage(clientMsg);
    }

    public Flux<String> sendStream(UUID sessionId, String content) {
        if (content == null || content.isBlank()) {
            return Flux.error(new BadRequestException("Пустое сообщение"));
        }
        Context ctx = loadActive(sessionId);
        Message therapistMsg = messageRepository.save(Message.of(
                ctx.session().getId(), ctx.client().getId(), MessageRole.THERAPIST, content.trim()));
        ragService.indexMessage(therapistMsg, ctx.therapist().getId());

        StringBuilder acc = new StringBuilder();
        return llmClient.chatStream(buildClientMessages(ctx.client(), content.trim()))
                .doOnNext(acc::append)
                .doOnComplete(() -> {
                    Message clientMsg = messageRepository.save(Message.of(
                            ctx.session().getId(), ctx.client().getId(), MessageRole.CLIENT, acc.toString()));
                    ragService.indexMessage(clientMsg, ctx.therapist().getId());
                });
    }

    private List<LlmClient.ChatMessage> buildClientMessages(Client client, String latestTherapistText) {
        String memory = ragService.retrieveClientMemory(client.getId(), latestTherapistText, 5);
        List<Message> recent = messageRepository.findTop40ByClientIdOrderByCreatedAtDesc(client.getId());
        Collections.reverse(recent);

        StringJoiner system = new StringJoiner("\n\n");
        system.add(promptLoader.load("client-system.txt"));
        system.add("Карточка клиента (JSON):\n" + client.getPersonaJson());
        system.add("Теги поведения: " + String.join(", ", client.getBehaviorTags()));
        system.add("Защиты: " + String.join(", ", client.getDefenses()));
        system.add("Ядро проблемы: " + (client.getCoreProblem() == null ? client.getPresentingRequest() : client.getCoreProblem()));
        system.add("therapyProgress: " + client.getTherapyProgress() + " (0–100)");
        system.add("status: " + client.getStatus().name());
        if (!memory.isBlank()) {
            system.add("Релевантная память прошлых сессий:\n" + memory);
        }

        List<LlmClient.ChatMessage> messages = new ArrayList<>();
        messages.add(LlmClient.ChatMessage.system(system.toString()));
        for (Message m : recent) {
            if (m.getRole() == MessageRole.SYSTEM) {
                continue;
            }
            if (m.getRole() == MessageRole.THERAPIST) {
                messages.add(LlmClient.ChatMessage.user(m.getContent()));
            } else if (m.getRole() == MessageRole.CLIENT) {
                messages.add(LlmClient.ChatMessage.assistant(m.getContent()));
            }
        }
        return messages;
    }

    private Context loadActive(UUID sessionId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        TherapySession session = sessionRepository.findByIdAndTherapistId(sessionId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Сеанс не найден"));
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("Сеанс уже завершён");
        }
        if (session.isTimerPaused()) {
            throw new ConflictException("Снимите паузу кнопкой «Начать сеанс», чтобы продолжить разговор");
        }
        Client client = clientRepository.findByIdAndTherapistId(session.getClientId(), therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        return new Context(therapist, client, session);
    }

    private record Context(TherapistProfile therapist, Client client, TherapySession session) {
    }
}
