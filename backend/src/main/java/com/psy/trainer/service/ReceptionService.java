package com.psy.trainer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.psy.trainer.domain.Client;
import com.psy.trainer.domain.ClientGender;
import com.psy.trainer.domain.Difficulty;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.MessageRole;
import com.psy.trainer.domain.SessionStatus;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.domain.TherapySession;
import com.psy.trainer.llm.LlmCallContext;
import com.psy.trainer.llm.LlmClient;
import com.psy.trainer.llm.LlmUsageContext;
import com.psy.trainer.rag.RagService;
import com.psy.trainer.repo.ClientRepository;
import com.psy.trainer.repo.MessageRepository;
import com.psy.trainer.repo.TherapySessionRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ReceptionService {

    private final CurrentTherapistService currentTherapistService;
    private final ClientRepository clientRepository;
    private final TherapySessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final CaseService caseService;
    private final ObjectMapper objectMapper;
    private final ClientMapper mapper;
    private final RagService ragService;

    public ReceptionService(
            CurrentTherapistService currentTherapistService,
            ClientRepository clientRepository,
            TherapySessionRepository sessionRepository,
            MessageRepository messageRepository,
            LlmClient llmClient,
            PromptLoader promptLoader,
            CaseService caseService,
            ObjectMapper objectMapper,
            ClientMapper mapper,
            RagService ragService
    ) {
        this.currentTherapistService = currentTherapistService;
        this.clientRepository = clientRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.caseService = caseService;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.ragService = ragService;
    }

    @Transactional
    public Dtos.ReceptionResponse startNew(Dtos.StartReceptionRequest request) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        Difficulty preferred = request != null ? request.difficulty() : null;
        String caseId = request != null ? request.caseId() : null;
        ClientGender preferredGender = parsePreferredGender(request != null ? request.gender() : null);
        Integer preferredAge = parsePreferredAge(request != null ? request.age() : null);
        String storedCaseId = null;
        if (caseId != null && !caseId.isBlank()) {
            storedCaseId = caseService.requireAccessible(caseId).getId().toString();
        }

        // Persist client + session first so persona LLM usage is attributed as client chat.
        Difficulty seedDifficulty = preferred != null ? preferred : Difficulty.MEDIUM;
        int seedAge = preferredAge != null ? preferredAge : 30;
        Client client = clientRepository.save(Client.create(
                therapist.getId(),
                "Клиент",
                seedAge,
                preferredGender,
                "Загрузка персоны…",
                seedDifficulty,
                new String[]{"ANXIOUS"},
                new String[]{"избегание"},
                "{}",
                storedCaseId,
                "Загрузка персоны…"
        ));
        TherapySession session = sessionRepository.save(TherapySession.start(client.getId(), therapist.getId()));
        LlmCallContext chatCtx = LlmCallContext.of("chat", client.getId(), session.getId());

        JsonNode persona = generatePersona(preferred, storedCaseId, preferredGender, preferredAge, chatCtx);
        String name = text(persona, "name", "Клиент");
        int age = preferredAge != null ? preferredAge : clampAge(persona.path("age").asInt(30));
        ClientGender gender = preferredGender != null
                ? preferredGender
                : parseGenderFromPersona(persona.path("gender").asText(null));
        String presenting = text(persona, "presentingRequest", "Хочу разобраться в себе");
        Difficulty difficulty = parseDifficulty(persona.path("difficulty").asText("MEDIUM"), preferred);
        String[] tags = toStringArray(persona.path("behaviorTags"), List.of("ANXIOUS"));
        String[] defenses = toStringArray(persona.path("defenses"), List.of("избегание"));
        String opening = text(persona, "openingLine", "Здравствуйте... я немного волнуюсь.");
        String coreProblem = text(persona, "coreProblem", presenting);

        client.applyGeneratedPersona(
                name,
                age,
                gender,
                presenting,
                difficulty,
                tags,
                defenses,
                persona.toString(),
                coreProblem
        );
        client = clientRepository.save(client);

        Message openingMessage = messageRepository.save(Message.of(session.getId(), client.getId(), MessageRole.CLIENT, opening));
        LlmUsageContext.run(
                chatCtx,
                () -> ragService.indexMessage(openingMessage, therapist.getId())
        );

        return new Dtos.ReceptionResponse(mapper.toClient(client), mapper.toSession(session), opening);
    }

    @Transactional
    public Dtos.SessionResponse continueClient(UUID clientId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        Client client = clientRepository.findByIdAndTherapistId(clientId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        if (client.getStatus() == com.psy.trainer.domain.ClientStatus.COMPLETED) {
            throw new ConflictException("Клиент завершил терапию — новый сеанс не нужен");
        }
        sessionRepository.findByClientIdAndStatus(clientId, SessionStatus.ACTIVE).ifPresent(s -> {
            throw new ConflictException("У клиента уже есть активный сеанс");
        });
        TherapySession session = sessionRepository.save(TherapySession.start(client.getId(), therapist.getId()));
        long sessionNo = sessionRepository.countByClientId(clientId);
        messageRepository.save(Message.of(
                session.getId(),
                client.getId(),
                MessageRole.SYSTEM,
                "Начат сеанс №" + sessionNo
        ));
        String opening = generateReturnOpening(client, session.getId());
        Message openingMessage = messageRepository.save(Message.of(
                session.getId(), client.getId(), MessageRole.CLIENT, opening));
        LlmUsageContext.run(
                LlmCallContext.of("chat", client.getId(), session.getId()),
                () -> ragService.indexMessage(openingMessage, therapist.getId())
        );
        return mapper.toSession(session);
    }

    @Transactional
    public Dtos.SessionResponse endSession(UUID sessionId, Integer elapsedSeconds) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        TherapySession session = sessionRepository.findByIdAndTherapistId(sessionId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Сеанс не найден"));
        if (session.getStatus() == SessionStatus.CLOSED) {
            return mapper.toSession(session);
        }
        if (elapsedSeconds != null) {
            session.syncElapsed(elapsedSeconds);
        }
        session.end();
        sessionRepository.save(session);
        Client client = clientRepository.findByIdAndTherapistId(session.getClientId(), therapist.getId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
        evaluateTherapyProgress(client, session.getId());
        clientRepository.save(client);
        return mapper.toSession(session);
    }

    @Transactional
    public Dtos.SessionResponse updateTimer(UUID sessionId, Dtos.TimerUpdateRequest request) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        TherapySession session = sessionRepository.findByIdAndTherapistId(sessionId, therapist.getId())
                .orElseThrow(() -> new NotFoundException("Сеанс не найден"));
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("Сеанс уже завершён");
        }
        boolean pause = request != null && Boolean.TRUE.equals(request.paused());
        boolean resume = request != null && Boolean.FALSE.equals(request.paused());
        if (request != null && request.elapsedSeconds() != null) {
            session.syncElapsed(request.elapsedSeconds());
        }
        if (pause) {
            session.pauseTimer();
        } else if (resume) {
            session.resumeTimer();
        }
        return mapper.toSession(sessionRepository.save(session));
    }

    private String generateReturnOpening(Client client, UUID sessionId) {
        LlmCallContext usageCtx = LlmCallContext.of("chat", client.getId(), sessionId);
        return LlmUsageContext.call(usageCtx, () -> {
            String memory = ragService.retrieveClientMemory(client.getId(), client.getPresentingRequest(), 4);
            String user = """
                    Клиент: %s, %d
                    Запрос: %s
                    Ядро проблемы: %s
                    therapyProgress: %d
                    status: %s
                    Память:
                    %s
                    """.formatted(
                    client.getName(),
                    client.getAge(),
                    client.getPresentingRequest(),
                    client.getCoreProblem() == null ? client.getPresentingRequest() : client.getCoreProblem(),
                    client.getTherapyProgress(),
                    client.getStatus().name(),
                    memory.isBlank() ? "(мало памяти)" : memory
            );
            try {
                return llmClient.chat(List.of(
                        LlmClient.ChatMessage.system(promptLoader.load("session-return.txt")),
                        LlmClient.ChatMessage.user(user)
                ), usageCtx).trim();
            } catch (Exception e) {
                return "Здравствуйте… я снова здесь. С прошлого раза много думал(а) о нашем разговоре.";
            }
        });
    }

    private void evaluateTherapyProgress(Client client, UUID sessionId) {
        List<Message> recent = messageRepository.findTop40ByClientIdOrderByCreatedAtDesc(client.getId());
        StringBuilder dialog = new StringBuilder();
        for (int i = recent.size() - 1; i >= 0; i--) {
            Message m = recent.get(i);
            if (m.getSessionId() != null && m.getSessionId().equals(sessionId)
                    && (m.getRole() == MessageRole.THERAPIST || m.getRole() == MessageRole.CLIENT)) {
                dialog.append(m.getRole().name()).append(": ").append(m.getContent()).append('\n');
            }
        }
        if (dialog.isEmpty()) {
            return;
        }
        String user = """
                previousProgress: %d
                coreProblem: %s
                presentingRequest: %s
                Диалог сессии:
                %s
                """.formatted(
                client.getTherapyProgress(),
                client.getCoreProblem() == null ? "" : client.getCoreProblem(),
                client.getPresentingRequest(),
                dialog
        );
        LlmCallContext usageCtx = LlmCallContext.of("progress", client.getId(), sessionId);
        try {
            String raw = llmClient.chatJson(List.of(
                    LlmClient.ChatMessage.system(promptLoader.load("therapy-progress.txt")),
                    LlmClient.ChatMessage.user(user)
            ), usageCtx);
            JsonNode json = objectMapper.readTree(extractJson(raw));
            int progress = json.path("progress").asInt(client.getTherapyProgress());
            boolean resolved = json.path("resolved").asBoolean(false);
            String core = text(json, "coreProblem", client.getCoreProblem());
            client.updateTherapyState(progress, core, resolved && progress >= 75);
        } catch (Exception e) {
            // keep previous progress on parse/LLM failure
        }
    }

    private JsonNode generatePersona(
            Difficulty preferred,
            String caseId,
            ClientGender preferredGender,
            Integer preferredAge,
            LlmCallContext usageCtx
    ) {
        StringBuilder user = new StringBuilder("Сгенерируй клиента.");
        if (preferredGender != null) {
            user.append(" Пол (гендерная социализация) строго: ").append(preferredGender.name())
                    .append(" (").append(preferredGender.labelRu()).append(").")
                    .append(' ').append(genderHint(preferredGender));
        } else {
            user.append(" Пол выбери сам (MALE или FEMALE) и укажи в JSON поле gender.");
        }
        if (preferredAge != null) {
            user.append(" Возраст строго: ").append(preferredAge).append(" лет.")
                    .append(' ').append(generationHint(preferredAge));
        } else {
            user.append(" Возраст выбери сам в диапазоне 18–80 и укажи в JSON поле age.");
        }
        if (preferred != null) {
            user.append(" Сложность: ").append(preferred.name()).append('.');
        }
        if (caseId != null) {
            caseService.findByIdOrSlug(caseId).ifPresent(c -> user
                    .append(" Опора на кейс: ").append(c.getTitle())
                    .append(". Запрос: ").append(c.getPresentingRequest())
                    .append(". Желательные теги: ").append(Arrays.toString(c.getSuggestedTags())));
        }
        String raw = llmClient.chatJson(List.of(
                LlmClient.ChatMessage.system(promptLoader.load("persona-generation.txt")),
                LlmClient.ChatMessage.user(user.toString())
        ), usageCtx);
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode obj) {
                int age = preferredAge != null ? preferredAge : clampAge(obj.path("age").asInt(30));
                ClientGender gender = preferredGender != null
                        ? preferredGender
                        : parseGenderFromPersona(obj.path("gender").asText(null));
                obj.put("age", age);
                if (gender != null) {
                    obj.put("gender", gender.name());
                    obj.put("genderLabelRu", gender.labelRu());
                }
                obj.put("generationBand", generationBand(age));
            }
            return node;
        } catch (Exception e) {
            throw new BadRequestException("Не удалось разобрать персону клиента: " + e.getMessage());
        }
    }

    /** null = let the model choose */
    static ClientGender parsePreferredGender(String raw) {
        if (raw == null || raw.isBlank() || "ANY".equalsIgnoreCase(raw) || "NEUTRAL".equalsIgnoreCase(raw)) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        if ("M".equals(v) || "М".equals(v) || "MALE".equals(v) || "MAN".equals(v)) {
            return ClientGender.MALE;
        }
        if ("F".equals(v) || "Ж".equals(v) || "FEMALE".equals(v) || "WOMAN".equals(v)) {
            return ClientGender.FEMALE;
        }
        return null;
    }

    /** null = let the model choose */
    static Integer parsePreferredAge(Integer raw) {
        if (raw == null) {
            return null;
        }
        if (raw < 18 || raw > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Возраст должен быть от 18 до 80");
        }
        return raw;
    }

    static ClientGender parseGenderFromPersona(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        if (v.startsWith("M") || v.startsWith("М") || v.contains("MALE") || v.contains("МУЖ")) {
            return ClientGender.MALE;
        }
        if (v.startsWith("F") || v.startsWith("Ж") || v.contains("FEMALE") || v.contains("ЖЕН")) {
            return ClientGender.FEMALE;
        }
        return null;
    }

    static int clampAge(int age) {
        if (age < 18) return 18;
        if (age > 80) return 80;
        return age;
    }

    static String generationBand(int age) {
        if (age <= 24) return "YOUNG_ADULT";
        if (age <= 34) return "EARLY_ADULT";
        if (age <= 49) return "MIDLIFE";
        if (age <= 64) return "MATURE";
        return "LATE_ADULT";
    }

    static String generationHint(int age) {
        return switch (generationBand(age)) {
            case "YOUNG_ADULT" ->
                    "Когорта 18–24: становление идентичности, сверстники и семья происхождения ещё громко звучат, "
                            + "сравнение в соцсетях, тема «кем я становлюсь», стыд перед ровесниками, язык ближе к молодёжному.";
            case "EARLY_ADULT" ->
                    "Когорта 25–34: давление вех (карьера/близость/«уже должен(на) устроить жизнь»), край выгорания, "
                            + "фигура долженствования и сравнение с «успевшими».";
            case "MIDLIFE" ->
                    "Когорта 35–49: сжимающие роли (работа/партнёрство/дети/родители), середина жизни, "
                            + "вопрос «это всё?», больше жизненной истории и телесных сигналов усталости.";
            case "MATURE" ->
                    "Когорта 50–64: переоценка пути, пустое гнездо / стареющие родители, смысл против статуса, "
                            + "опыт и усталость от старых сценариев, темп чуть медленнее.";
            default ->
                    "Когорта 65–80: пределы, потери, наследие, одиночество vs свобода, иной темп и отношение к авторитету "
                            + "терапевта; опора на биографию, меньше цифрового сленга.";
        };
    }

    static String genderHint(ClientGender gender) {
        if (gender == ClientGender.MALE) {
            return "Мужская социализация (мягкий уклон, не стереотип-тюрьма): чаще прерывание контакта уходом в голову/"
                    + "контроль/минимизацию уязвимости; стыд вокруг «слабости»; тело как напряжение и контроль; "
                    + "может быть дистантным или самодостаточным. Теги поведения всё равно любые — он может плакать, "
                    + "сливаться, обесценивать.";
        }
        return "Женская социализация (мягкий уклон, не стереотип-тюрьма): чаще доступнее аффект и реляционное поле; "
                + "фигуры вины/заботы/одобрения; телесные маркеры легче называются. Теги любые — она может "
                + "интеллектуализировать, обесценивать, тестировать границы.";
    }

    private static String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    private static String text(JsonNode node, String field, String fallback) {
        String v = node.path(field).asText(null);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static Difficulty parseDifficulty(String raw, Difficulty preferred) {
        if (preferred != null) {
            return preferred;
        }
        try {
            return Difficulty.valueOf(raw);
        } catch (Exception e) {
            return Difficulty.MEDIUM;
        }
    }

    private static String[] toStringArray(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return fallback.toArray(String[]::new);
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> {
            if (!n.asText().isBlank()) {
                values.add(n.asText());
            }
        });
        return values.isEmpty() ? fallback.toArray(String[]::new) : values.toArray(String[]::new);
    }
}
