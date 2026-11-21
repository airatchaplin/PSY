package com.psy.trainer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.psy.trainer.service.BadRequestException;
import com.psy.trainer.service.RuntimeConfigService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * RouterAI OpenAI-compatible API helpers.
 * Reference: https://routerai.ru/docs/reference
 * Known endpoints used here: GET /credits, GET /key, GET /models
 */
@Component
public class RouterAiClient {

    private final WebClient webClient;
    private final RuntimeConfigService runtimeConfigService;

    public RouterAiClient(WebClient.Builder builder, RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
        this.webClient = builder.build();
    }

    public BigDecimal creditsBalance() {
        ensureApiKey();
        JsonNode response = get("/credits");
        JsonNode credits = response.path("data").path("credits");
        if (credits.isMissingNode() || !credits.isNumber()) {
            JsonNode total = response.path("data").path("total_credits");
            JsonNode usage = response.path("data").path("total_usage");
            if (total.isNumber() && usage.isNumber()) {
                return BigDecimal.valueOf(total.asDouble() - usage.asDouble());
            }
            throw new BadRequestException("Не удалось прочитать баланс RouterAI (/credits)");
        }
        return BigDecimal.valueOf(credits.asDouble());
    }

    public KeyInfo keyInfo() {
        ensureApiKey();
        JsonNode data = get("/key").path("data");
        return new KeyInfo(
                data.path("name").asText(null),
                data.path("usage").isNumber() ? BigDecimal.valueOf(data.path("usage").asDouble()) : null,
                data.path("limit").isNumber() ? BigDecimal.valueOf(data.path("limit").asDouble()) : null,
                data.path("limit_remaining").isNumber() ? BigDecimal.valueOf(data.path("limit_remaining").asDouble()) : null,
                data.path("usage_monthly").isNumber() ? BigDecimal.valueOf(data.path("usage_monthly").asDouble()) : null,
                data.path("limit_reset").asText(null),
                data.path("disabled").asBoolean(false)
        );
    }

    private final java.util.concurrent.ConcurrentHashMap<String, ModelInfo> modelCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long modelCacheAtMs = 0L;

    public List<ModelInfo> listModels() {
        ensureApiKey();
        JsonNode data = get("/models").path("data");
        List<ModelInfo> out = new ArrayList<>();
        if (!data.isArray()) {
            return out;
        }
        for (JsonNode n : data) {
            JsonNode pricing = n.path("pricing");
            String modality = n.path("architecture").path("modality").asText("");
            ModelInfo info = new ModelInfo(
                    n.path("id").asText(),
                    n.path("name").asText(n.path("id").asText()),
                    n.path("context_length").isNumber() ? n.path("context_length").asInt() : null,
                    decimalOrNull(pricing.path("prompt")),
                    decimalOrNull(pricing.path("completion")),
                    extractOutputModalities(n),
                    modality
            );
            out.add(info);
            if (info.id() != null && !info.id().isBlank()) {
                modelCache.put(info.id(), info);
            }
        }
        modelCacheAtMs = System.currentTimeMillis();
        return out;
    }

    /** Prompt price per token (₽), cached from /models. */
    public BigDecimal promptPricePerToken(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        ModelInfo cached = modelCache.get(modelId);
        if (cached == null || System.currentTimeMillis() - modelCacheAtMs > 10 * 60_000L) {
            try {
                listModels();
            } catch (Exception ignored) {
                // keep stale cache if refresh fails
            }
            cached = modelCache.get(modelId);
        }
        return cached == null ? null : cached.promptPricePerToken();
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble());
    }

    private static List<String> extractOutputModalities(JsonNode model) {
        List<String> out = new ArrayList<>();
        JsonNode arch = model.path("architecture");
        JsonNode outputs = arch.path("output_modalities");
        if (outputs.isArray()) {
            for (JsonNode m : outputs) {
                if (m.isTextual() && !m.asText().isBlank()) {
                    out.add(m.asText().trim().toLowerCase());
                }
            }
        }
        if (out.isEmpty()) {
            String modality = arch.path("modality").asText("");
            // e.g. "text->audio" / "text+audio->text"
            int arrow = modality.indexOf("->");
            if (arrow >= 0) {
                String right = modality.substring(arrow + 2).toLowerCase();
                for (String part : right.split("[+,]")) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        out.add(p);
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    private JsonNode get(String path) {
        try {
            JsonNode response = webClient.get()
                    .uri(URI.create(runtimeConfigService.getBaseUrl() + path))
                    .headers(h -> h.setBearerAuth(runtimeConfigService.getApiKey()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null) {
                throw new BadRequestException("Пустой ответ RouterAI " + path);
            }
            return response;
        } catch (WebClientResponseException e) {
            throw new BadRequestException("RouterAI " + path + ": HTTP " + e.getStatusCode().value()
                    + " " + e.getResponseBodyAsString());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("RouterAI " + path + ": " + e.getMessage());
        }
    }

    private void ensureApiKey() {
        if (!runtimeConfigService.hasApiKey()) {
            throw new BadRequestException("API key не задан — укажите его в админке");
        }
        if (runtimeConfigService.getBaseUrl().isBlank()) {
            throw new BadRequestException("Base URL не задан — укажите его в админке");
        }
    }

    public record KeyInfo(
            String name,
            BigDecimal usage,
            BigDecimal limit,
            BigDecimal limitRemaining,
            BigDecimal usageMonthly,
            String limitReset,
            boolean disabled
    ) {
    }

    public record ModelInfo(
            String id,
            String name,
            Integer contextLength,
            BigDecimal promptPricePerToken,
            BigDecimal completionPricePerToken,
            List<String> outputModalities,
            String modality
    ) {
        /** True TTS for {@code /audio/speech}: text→speech (not multimodal chat-audio). */
        public boolean isSpeechTts() {
            if (outputModalities != null && outputModalities.stream().anyMatch(m -> "speech".equalsIgnoreCase(m))) {
                return true;
            }
            String mod = modality == null ? "" : modality.toLowerCase();
            if (mod.endsWith("->speech") || "text->speech".equals(mod)) {
                return true;
            }
            // Fallback when architecture fields are missing from /models payload.
            String hay = ((id == null ? "" : id) + " " + (name == null ? "" : name)).toLowerCase();
            if (hay.contains("gpt-audio") || hay.contains("lyria")) {
                return false;
            }
            return hay.contains("tts")
                    || hay.contains("voice-tts")
                    || hay.contains("grok-voice")
                    || hay.contains("mai-voice")
                    || hay.contains("kokoro")
                    || hay.contains("orpheus")
                    || hay.contains("assemblyai-tts")
                    || hay.contains("text-to-speech");
        }

        public boolean isEmbedding() {
            if (outputModalities != null && outputModalities.stream().anyMatch(m ->
                    "embeddings".equalsIgnoreCase(m) || "embedding".equalsIgnoreCase(m))) {
                return true;
            }
            String mod = modality == null ? "" : modality.toLowerCase();
            if (mod.contains("->embeddings") || mod.endsWith("embeddings")) {
                return true;
            }
            String hay = ((id == null ? "" : id) + " " + (name == null ? "" : name)).toLowerCase();
            return hay.contains("embed");
        }

        /** Dedicated STT for {@code /audio/transcriptions}: audio→transcription. */
        public boolean isTranscriptionStt() {
            String mod = modality == null ? "" : modality.toLowerCase();
            if (mod.equals("audio->transcription") || mod.equals("speech->text") || mod.equals("audio->text")) {
                return true;
            }
            if (outputModalities != null && outputModalities.stream().anyMatch(m ->
                    "transcription".equalsIgnoreCase(m))) {
                return true;
            }
            String hay = ((id == null ? "" : id) + " " + (name == null ? "" : name)).toLowerCase();
            if (hay.contains("tts") || hay.contains("voice-tts") || hay.contains("embed")) {
                return false;
            }
            return hay.contains("whisper")
                    || hay.contains("transcrib")
                    || hay.contains("-stt")
                    || hay.contains("stt-")
                    || hay.contains("grok-stt")
                    || hay.contains("speech-to-text")
                    || hay.contains("nova-3");
        }
    }
}
