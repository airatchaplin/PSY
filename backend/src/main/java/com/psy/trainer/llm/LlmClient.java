package com.psy.trainer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psy.trainer.service.BadRequestException;
import com.psy.trainer.service.LlmUsageService;
import com.psy.trainer.service.RuntimeConfigService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RuntimeConfigService runtimeConfigService;
    private final LlmUsageService llmUsageService;

    public LlmClient(
            WebClient.Builder builder,
            ObjectMapper objectMapper,
            RuntimeConfigService runtimeConfigService,
            LlmUsageService llmUsageService
    ) {
        this.objectMapper = objectMapper;
        this.runtimeConfigService = runtimeConfigService;
        this.llmUsageService = llmUsageService;
        this.webClient = builder.build();
    }

    public String chat(List<ChatMessage> messages) {
        return chat(messages, "chat");
    }

    public String chat(List<ChatMessage> messages, String purpose) {
        ensureConfigured();
        String model = runtimeConfigService.getModel();
        Map<String, Object> body = baseBody(messages, false, model);
        JsonNode response = webClient.post()
                .uri(uri("/chat/completions"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(runtimeConfigService.getApiKey()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null) {
            throw new BadRequestException("Пустой ответ LLM");
        }
        recordUsage(response, model, purpose);
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new BadRequestException("LLM не вернул текст");
        }
        return content.asText();
    }

    public String chatJson(List<ChatMessage> messages) {
        return chatJson(messages, "json");
    }

    public String chatJson(List<ChatMessage> messages, String purpose) {
        List<ChatMessage> withHint = new ArrayList<>(messages);
        withHint.add(ChatMessage.system("Ответь строго валидным JSON без markdown-обёртки."));
        return chat(withHint, purpose);
    }

    public Flux<String> chatStream(List<ChatMessage> messages) {
        ensureConfigured();
        String model = runtimeConfigService.getModel();
        Map<String, Object> body = baseBody(messages, true, model);
        return webClient.post()
                .uri(uri("/chat/completions"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(runtimeConfigService.getApiKey()))
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(chunk -> Flux.fromIterable(parseSseChunks(chunk)))
                .doOnComplete(() -> llmUsageService.record(model, "stream", null, null, null, null, null));
    }

    public float[] embed(String text) {
        ensureConfigured();
        String embeddingModel = runtimeConfigService.getEmbeddingModel();
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new BadRequestException("Embedding-модель не задана — укажите её в админке");
        }
        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", text
        );
        JsonNode response = webClient.post()
                .uri(uri("/embeddings"))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(runtimeConfigService.getApiKey()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null) {
            throw new BadRequestException("Пустой ответ embeddings");
        }
        recordUsage(response, embeddingModel, "embed");
        JsonNode arr = response.path("data").path(0).path("embedding");
        if (!arr.isArray()) {
            throw new BadRequestException("Некорректный embedding");
        }
        float[] vector = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vector[i] = (float) arr.get(i).asDouble();
        }
        return vector;
    }

    private URI uri(String path) {
        return URI.create(runtimeConfigService.getBaseUrl() + path);
    }

    private void recordUsage(JsonNode response, String model, String purpose) {
        try {
            JsonNode usage = response.path("usage");
            Integer prompt = usage.path("prompt_tokens").isNumber() ? usage.path("prompt_tokens").asInt() : null;
            Integer completion = usage.path("completion_tokens").isNumber() ? usage.path("completion_tokens").asInt() : null;
            Integer total = usage.path("total_tokens").isNumber() ? usage.path("total_tokens").asInt() : null;
            BigDecimal cost = null;
            if (usage.path("cost").isNumber()) {
                cost = BigDecimal.valueOf(usage.path("cost").asDouble());
            } else if (usage.path("total_cost").isNumber()) {
                cost = BigDecimal.valueOf(usage.path("total_cost").asDouble());
            }
            String generationId = response.path("id").asText(null);
            llmUsageService.record(model, purpose, prompt, completion, total, cost, generationId);
        } catch (Exception ignored) {
            // usage logging must not break chat
        }
    }

    private Map<String, Object> baseBody(List<ChatMessage> messages, boolean stream, String model) {
        List<Map<String, String>> mapped = messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", mapped);
        body.put("temperature", 0.8);
        body.put("stream", stream);
        return body;
    }

    private List<String> parseSseChunks(String raw) {
        List<String> tokens = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String data = trimmed.substring(5).trim();
            if ("[DONE]".equals(data)) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(data);
                JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                if (!delta.isMissingNode() && !delta.asText().isEmpty()) {
                    tokens.add(delta.asText());
                }
            } catch (Exception ignored) {
                // skip malformed chunks
            }
        }
        return tokens;
    }

    private void ensureConfigured() {
        if (!runtimeConfigService.hasApiKey()) {
            throw new BadRequestException("API key не задан — укажите его в админке");
        }
        if (runtimeConfigService.getBaseUrl().isBlank()) {
            throw new BadRequestException("Base URL не задан — укажите его в админке");
        }
        if (runtimeConfigService.getModel().isBlank()) {
            throw new BadRequestException("Модель не задана — выберите её в админке");
        }
    }

    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }
}
