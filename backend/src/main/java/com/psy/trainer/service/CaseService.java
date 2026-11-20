package com.psy.trainer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psy.trainer.domain.BehaviorTag;
import com.psy.trainer.domain.CaseSource;
import com.psy.trainer.domain.Difficulty;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.domain.TherapyCaseEntity;
import com.psy.trainer.llm.LlmCallContext;
import com.psy.trainer.llm.LlmClient;
import com.psy.trainer.llm.LlmUsageContext;
import com.psy.trainer.rag.RagService;
import com.psy.trainer.repo.TherapistProfileRepository;
import com.psy.trainer.repo.TherapyCaseRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private final TherapyCaseRepository caseRepository;
    private final TherapistProfileRepository therapistProfileRepository;
    private final CurrentTherapistService currentTherapistService;
    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final LabelMapper labelMapper;
    private final RagService ragService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CaseService(
            TherapyCaseRepository caseRepository,
            TherapistProfileRepository therapistProfileRepository,
            CurrentTherapistService currentTherapistService,
            LlmClient llmClient,
            PromptLoader promptLoader,
            ObjectMapper objectMapper,
            LabelMapper labelMapper,
            RagService ragService
    ) {
        this.caseRepository = caseRepository;
        this.therapistProfileRepository = therapistProfileRepository;
        this.currentTherapistService = currentTherapistService;
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.objectMapper = objectMapper;
        this.labelMapper = labelMapper;
        this.ragService = ragService;
    }

    @Transactional(readOnly = true)
    public List<Dtos.CaseResponse> list() {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        return caseRepository.findVisibleFor(therapist.getId()).stream()
                .map(c -> toResponse(c, therapist.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.TherapistBrief> listShareTargets() {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        return therapistProfileRepository.findByIdNotOrderByDisplayNameAsc(therapist.getId()).stream()
                .map(t -> new Dtos.TherapistBrief(t.getId(), t.getDisplayName(), t.getEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TherapyCaseEntity> findByIdOrSlug(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(caseId);
            return caseRepository.findById(id);
        } catch (IllegalArgumentException e) {
            return caseRepository.findBySlug(caseId);
        }
    }

    @Transactional(readOnly = true)
    public TherapyCaseEntity requireAccessible(String caseId) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        TherapyCaseEntity c = findByIdOrSlug(caseId)
                .orElseThrow(() -> new NotFoundException("Кейс не найден"));
        if (!isVisibleTo(c, therapist.getId())) {
            throw new NotFoundException("Кейс не найден");
        }
        return c;
    }

    @Transactional
    public Dtos.CaseResponse createManual(Dtos.CreateCaseRequest request) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        validateManual(request);
        TherapyCaseEntity entity = TherapyCaseEntity.create(
                request.title().trim(),
                request.presentingRequest().trim(),
                request.difficulty() != null ? request.difficulty() : Difficulty.MEDIUM,
                normalizeTags(request.suggestedTags()),
                blankToNull(request.notes()),
                CaseSource.MANUAL,
                therapist.getId(),
                null,
                null
        );
        return toResponse(caseRepository.save(entity), therapist.getId());
    }

    @Transactional
    public Dtos.CaseResponse generate(Dtos.GenerateCaseRequest request) {
        TherapistProfile therapist = currentTherapistService.requireCurrent();
        String prompt = request != null ? request.prompt() : null;
        boolean hasPrompt = prompt != null && !prompt.isBlank();
        CaseSource source = hasPrompt ? CaseSource.AI_PROMPT : CaseSource.AI_AUTO;

        String existing = caseRepository.findVisibleFor(therapist.getId()).stream()
                .map(c -> "- " + c.getTitle() + ": " + c.getPresentingRequest())
                .collect(Collectors.joining("\n"));

        StringBuilder user = new StringBuilder();
        if (hasPrompt) {
            user.append("Описание / промпт пользователя:\n").append(prompt.trim()).append("\n\n");
        } else {
            user.append("Сгенерируй новый учебный кейс автоматически.\n\n");
        }
        user.append("Уже существующие кейсы (не повторяй):\n")
                .append(existing.isBlank() ? "(пока пусто)" : existing);

        UUID caseId = UUID.randomUUID();
        LlmCallContext usageCtx = LlmCallContext.of("case", null, null, caseId);
        String raw = LlmUsageContext.call(usageCtx, () -> llmClient.chatJson(List.of(
                LlmClient.ChatMessage.system(promptLoader.load("case-generation.txt")),
                LlmClient.ChatMessage.user(user.toString())
        ), usageCtx));

        try {
            JsonNode json = objectMapper.readTree(extractJson(raw));
            String title = text(json, "title", "Новый кейс");
            String presenting = text(json, "presentingRequest", "Хочу разобраться в себе");
            Difficulty difficulty = parseDifficulty(json.path("difficulty").asText("MEDIUM"));
            String[] tags = normalizeTags(readTags(json.path("suggestedTags")));
            String notes = blankToNull(text(json, "notes", null));

            TherapyCaseEntity entity = TherapyCaseEntity.create(
                    caseId,
                    title,
                    presenting,
                    difficulty,
                    tags,
                    notes,
                    source,
                    therapist.getId(),
                    hasPrompt ? prompt.trim() : null,
                    null,
                    null
            );
            return toResponse(caseRepository.save(entity), therapist.getId());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Не удалось сгенерировать кейс: " + e.getMessage());
        }
    }

    @Transactional
    public Dtos.ShareCaseResponse share(UUID caseId, Dtos.ShareCaseRequest request) {
        TherapistProfile owner = currentTherapistService.requireCurrent();
        TherapyCaseEntity original = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Кейс не найден"));
        if (original.getSource() == CaseSource.BUILTIN) {
            throw new BadRequestException("Базовые кейсы уже доступны всем");
        }
        if (original.getCreatedBy() == null || !original.getCreatedBy().equals(owner.getId())) {
            throw new BadRequestException("Поделиться можно только своим кейсом");
        }

        List<TherapistProfile> targets = resolveTargets(owner.getId(), request);
        if (targets.isEmpty()) {
            throw new BadRequestException("Выберите хотя бы одного терапевта");
        }

        UUID originId = original.getOriginCaseId() != null ? original.getOriginCaseId() : original.getId();
        List<String> recipients = new ArrayList<>();
        int shared = 0;
        for (TherapistProfile target : targets) {
            if (caseRepository.existsByOriginCaseIdAndCreatedBy(originId, target.getId())) {
                continue;
            }
            String shareNote = appendShareNote(original.getNotes(), owner.getDisplayName());
            TherapyCaseEntity copy = TherapyCaseEntity.create(
                    original.getTitle(),
                    original.getPresentingRequest(),
                    original.getDifficulty(),
                    original.getSuggestedTags(),
                    shareNote,
                    CaseSource.SHARED_COPY,
                    target.getId(),
                    original.getPromptUsed(),
                    null,
                    originId
            );
            caseRepository.save(copy);
            recipients.add(target.getDisplayName() != null ? target.getDisplayName() : target.getEmail());
            shared++;
        }
        return new Dtos.ShareCaseResponse(shared, recipients);
    }

    @Transactional
    public Dtos.CaseShareLinkResponse createShareLink(UUID caseId) {
        TherapistProfile owner = currentTherapistService.requireCurrent();
        TherapyCaseEntity original = requireOwnShareable(caseId, owner);
        if (original.getShareToken() == null || original.getShareToken().isBlank()) {
            original.setShareToken(newShareToken());
            caseRepository.save(original);
        }
        String token = original.getShareToken();
        return new Dtos.CaseShareLinkResponse(token, "/cases/shared/" + token);
    }

    @Transactional
    public Dtos.ImportSharedCaseResponse importByShareToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Некорректная ссылка");
        }
        TherapistProfile recipient = currentTherapistService.requireCurrent();
        TherapyCaseEntity original = caseRepository.findByShareToken(token.trim())
                .orElseThrow(() -> new NotFoundException("Ссылка недействительна или устарела"));
        if (original.getSource() == CaseSource.BUILTIN) {
            throw new BadRequestException("Базовые кейсы уже доступны всем");
        }
        if (original.getCreatedBy() != null && original.getCreatedBy().equals(recipient.getId())) {
            return new Dtos.ImportSharedCaseResponse(toResponse(original, recipient.getId()), true);
        }

        UUID originId = original.getOriginCaseId() != null ? original.getOriginCaseId() : original.getId();
        if (caseRepository.existsByOriginCaseIdAndCreatedBy(originId, recipient.getId())) {
            TherapyCaseEntity existing = caseRepository.findVisibleFor(recipient.getId()).stream()
                    .filter(c -> originId.equals(c.getOriginCaseId()) || originId.equals(c.getId()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return new Dtos.ImportSharedCaseResponse(toResponse(existing, recipient.getId()), true);
            }
        }

        String ownerName = original.getCreatedBy() == null
                ? null
                : therapistProfileRepository.findById(original.getCreatedBy())
                .map(TherapistProfile::getDisplayName)
                .orElse(null);
        String shareNote = appendShareNote(original.getNotes(), ownerName);
        TherapyCaseEntity copy = TherapyCaseEntity.create(
                original.getTitle(),
                original.getPresentingRequest(),
                original.getDifficulty(),
                original.getSuggestedTags(),
                shareNote,
                CaseSource.SHARED_COPY,
                recipient.getId(),
                original.getPromptUsed(),
                null,
                originId
        );
        TherapyCaseEntity saved = caseRepository.save(copy);
        return new Dtos.ImportSharedCaseResponse(toResponse(saved, recipient.getId()), false);
    }

    private TherapyCaseEntity requireOwnShareable(UUID caseId, TherapistProfile owner) {
        TherapyCaseEntity original = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Кейс не найден"));
        if (original.getSource() == CaseSource.BUILTIN) {
            throw new BadRequestException("Базовые кейсы уже доступны всем");
        }
        if (original.getCreatedBy() == null || !original.getCreatedBy().equals(owner.getId())) {
            throw new BadRequestException("Поделиться можно только своим кейсом");
        }
        return original;
    }

    private String newShareToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void reindexAllIntoRag() {
        // Only builtins go to shared gestalt_knowledge — private cases must not leak via RAG.
        caseRepository.findBySourceOrderByCreatedAtDesc(CaseSource.BUILTIN)
                .forEach(ragService::indexTherapyCase);
    }

    private List<TherapistProfile> resolveTargets(UUID ownerId, Dtos.ShareCaseRequest request) {
        if (request != null && request.all()) {
            return therapistProfileRepository.findByIdNotOrderByDisplayNameAsc(ownerId);
        }
        if (request == null || request.therapistIds() == null || request.therapistIds().isEmpty()) {
            return List.of();
        }
        Set<UUID> unique = new LinkedHashSet<>(request.therapistIds());
        unique.remove(ownerId);
        List<TherapistProfile> out = new ArrayList<>();
        for (UUID id : unique) {
            therapistProfileRepository.findById(id).ifPresent(out::add);
        }
        return out;
    }

    private static boolean isVisibleTo(TherapyCaseEntity c, UUID therapistId) {
        return c.getSource() == CaseSource.BUILTIN
                || (c.getCreatedBy() != null && c.getCreatedBy().equals(therapistId));
    }

    private Dtos.CaseResponse toResponse(TherapyCaseEntity c, UUID therapistId) {
        boolean builtin = c.getSource() == CaseSource.BUILTIN;
        boolean mine = c.getCreatedBy() != null && c.getCreatedBy().equals(therapistId);
        boolean canShare = mine && !builtin;
        String visibility = builtin ? "базовый"
                : c.getSource() == CaseSource.SHARED_COPY ? "полученный"
                : "мой";
        return new Dtos.CaseResponse(
                c.getId(),
                c.getSlug(),
                c.getTitle(),
                c.getPresentingRequest(),
                c.getDifficulty(),
                labelMapper.difficultyRu(c.getDifficulty()),
                labelMapper.toTags(c.getSuggestedTags()),
                c.getNotes(),
                c.getSource(),
                c.getCreatedAt(),
                mine,
                builtin,
                canShare,
                visibility
        );
    }

    private static String appendShareNote(String notes, String fromName) {
        String stamp = "Поделился: " + (fromName == null || fromName.isBlank() ? "коллега" : fromName);
        if (notes == null || notes.isBlank()) {
            return stamp;
        }
        if (notes.contains(stamp)) {
            return notes;
        }
        return notes.trim() + "\n" + stamp;
    }

    private void validateManual(Dtos.CreateCaseRequest request) {
        if (request == null
                || request.title() == null || request.title().isBlank()
                || request.presentingRequest() == null || request.presentingRequest().isBlank()) {
            throw new BadRequestException("Укажите название и запрос клиента");
        }
    }

    private static String[] normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[]{"ANXIOUS"};
        }
        List<String> valid = new ArrayList<>();
        for (String t : tags) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String code = t.trim().toUpperCase(Locale.ROOT);
            try {
                BehaviorTag.valueOf(code);
                valid.add(code);
            } catch (Exception ignored) {
                // skip unknown
            }
        }
        return valid.isEmpty() ? new String[]{"ANXIOUS"} : valid.toArray(String[]::new);
    }

    private static List<String> readTags(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static Difficulty parseDifficulty(String raw) {
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Difficulty.MEDIUM;
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        String v = node.path(field).asText(null);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
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
}
