package com.psy.trainer.rag;

import com.psy.trainer.config.PsyProperties;
import com.psy.trainer.domain.Message;
import com.psy.trainer.domain.TherapyCaseEntity;
import com.psy.trainer.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final QdrantClient qdrantClient;
    private final LlmClient llmClient;
    private final PsyProperties properties;
    private boolean knowledgeSeeded;

    public RagService(QdrantClient qdrantClient, LlmClient llmClient, PsyProperties properties) {
        this.qdrantClient = qdrantClient;
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public void init() {
        qdrantClient.ensureCollections();
        seedGestaltKnowledge();
    }

    public void indexMessage(Message message, UUID therapistId) {
        try {
            float[] vector = llmClient.embed(message.getContent());
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message.getContent());
            payload.put("clientId", message.getClientId().toString());
            payload.put("sessionId", message.getSessionId().toString());
            payload.put("therapistId", therapistId.toString());
            payload.put("role", message.getRole().name());
            payload.put("createdAt", message.getCreatedAt().toString());
            qdrantClient.upsert(
                    properties.getQdrant().getClientMemoryCollection(),
                    message.getId(),
                    vector,
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to index message {}: {}", message.getId(), e.getMessage());
        }
    }

    public void indexTherapyCase(TherapyCaseEntity therapyCase) {
        // Shared knowledge base: only global builtins. Private/user cases stay in Postgres.
        if (therapyCase.getSource() != com.psy.trainer.domain.CaseSource.BUILTIN) {
            return;
        }
        try {
            String tags = therapyCase.getSuggestedTags() == null
                    ? ""
                    : Arrays.stream(therapyCase.getSuggestedTags()).collect(Collectors.joining(", "));
            String text = """
                    Кейс: %s
                    Запрос: %s
                    Сложность: %s
                    Теги: %s
                    Заметки: %s
                    """.formatted(
                    therapyCase.getTitle(),
                    therapyCase.getPresentingRequest(),
                    therapyCase.getDifficulty().name(),
                    tags,
                    therapyCase.getNotes() == null ? "" : therapyCase.getNotes()
            ).trim();
            float[] vector = llmClient.embed(text);
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", text);
            payload.put("kind", "therapy_case");
            payload.put("caseId", therapyCase.getId().toString());
            payload.put("title", therapyCase.getTitle());
            payload.put("source", therapyCase.getSource().name());
            qdrantClient.upsert(
                    properties.getQdrant().getGestaltKnowledgeCollection(),
                    therapyCase.getId(),
                    vector,
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to index case {}: {}", therapyCase.getId(), e.getMessage());
        }
    }

    public String retrieveClientMemory(UUID clientId, String query, int limit) {
        try {
            float[] vector = llmClient.embed(query);
            List<QdrantClient.ScoredPoint> hits = qdrantClient.search(
                    properties.getQdrant().getClientMemoryCollection(),
                    vector,
                    limit,
                    Map.of("clientId", clientId.toString())
            );
            return hits.stream()
                    .map(QdrantClient.ScoredPoint::text)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.warn("Client memory retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    public String retrieveGestaltKnowledge(String query, int limit) {
        ensureKnowledgeSeeded();
        try {
            float[] vector = llmClient.embed(query);
            List<QdrantClient.ScoredPoint> hits = qdrantClient.search(
                    properties.getQdrant().getGestaltKnowledgeCollection(),
                    vector,
                    limit,
                    null
            );
            return hits.stream()
                    .map(QdrantClient.ScoredPoint::text)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.warn("Gestalt knowledge retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    private synchronized void ensureKnowledgeSeeded() {
        if (!knowledgeSeeded) {
            seedGestaltKnowledge();
        }
    }

    private void seedGestaltKnowledge() {
        Path dir = Path.of(properties.getKnowledgePath());
        if (!Files.isDirectory(dir)) {
            log.info("Knowledge path missing: {}", dir.toAbsolutePath());
            knowledgeSeeded = true;
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .forEach(this::indexKnowledgeFile);
            knowledgeSeeded = true;
        } catch (Exception e) {
            log.warn("Knowledge seed failed: {}", e.getMessage());
        }
    }

    private void indexKnowledgeFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String[] chunks = content.split("\n## ");
            for (int i = 0; i < chunks.length; i++) {
                String chunk = (i == 0 ? chunks[i] : "## " + chunks[i]).trim();
                if (chunk.length() < 40) {
                    continue;
                }
                float[] vector = llmClient.embed(chunk);
                UUID id = UUID.nameUUIDFromBytes((file.getFileName() + "#" + i).getBytes(StandardCharsets.UTF_8));
                qdrantClient.upsert(
                        properties.getQdrant().getGestaltKnowledgeCollection(),
                        id,
                        vector,
                        Map.of(
                                "text", chunk,
                                "source", file.getFileName().toString()
                        )
                );
            }
        } catch (Exception e) {
            log.warn("Failed to index {}: {}", file, e.getMessage());
        }
    }
}
