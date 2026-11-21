package com.psy.trainer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psy.trainer.service.BadRequestException;
import com.psy.trainer.service.LlmUsageService;
import com.psy.trainer.service.RuntimeConfigService;
import com.psy.trainer.service.UserBalanceService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
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
    private final UserBalanceService userBalanceService;
    private final RouterAiClient routerAiClient;

    public LlmClient(
            WebClient.Builder builder,
            ObjectMapper objectMapper,
            RuntimeConfigService runtimeConfigService,
            LlmUsageService llmUsageService,
            UserBalanceService userBalanceService,
            RouterAiClient routerAiClient
    ) {
        this.objectMapper = objectMapper;
        this.runtimeConfigService = runtimeConfigService;
        this.llmUsageService = llmUsageService;
        this.userBalanceService = userBalanceService;
        this.routerAiClient = routerAiClient;
        this.webClient = builder.build();
    }

    public String chat(List<ChatMessage> messages) {
        return chat(messages, resolveContext(LlmCallContext.of("chat")));
    }

    public String chat(List<ChatMessage> messages, String purpose) {
        return chat(messages, resolveContext(LlmCallContext.of(purpose)));
    }

    public String chat(List<ChatMessage> messages, LlmCallContext context) {
        LlmCallContext ctx = resolveContext(context);
        return LlmUsageContext.call(ctx, () -> {
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
            recordUsage(response, model, ctx);
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BadRequestException("LLM не вернул текст");
            }
            return content.asText();
        });
    }

    public String chatJson(List<ChatMessage> messages) {
        return chatJson(messages, LlmCallContext.of("json"));
    }

    public String chatJson(List<ChatMessage> messages, String purpose) {
        return chatJson(messages, LlmCallContext.of(purpose));
    }

    public String chatJson(List<ChatMessage> messages, LlmCallContext context) {
        List<ChatMessage> withHint = new ArrayList<>(messages);
        withHint.add(ChatMessage.system("Ответь строго валидным JSON без markdown-обёртки."));
        return chat(withHint, context);
    }

    public Flux<String> chatStream(List<ChatMessage> messages) {
        return chatStream(messages, resolveContext(LlmCallContext.of("chat")));
    }

    public Flux<String> chatStream(List<ChatMessage> messages, LlmCallContext context) {
        LlmCallContext ctx = resolveContext(context);
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
                .doOnComplete(() -> llmUsageService.record(
                        model,
                        "chat",
                        null,
                        null,
                        null,
                        null,
                        null,
                        ctx.clientId(),
                        ctx.sessionId()
                ));
    }

    /**
     * OpenAI-compatible TTS: {@code POST /audio/speech} → raw audio bytes.
     * Does not record usage — caller meters by character count.
     */
    public byte[] speech(String model, String voice, String input, String responseFormat) {
        userBalanceService.requireLlmAllowed();
        ensureApiConfigured();
        if (model == null || model.isBlank()) {
            throw new BadRequestException("TTS-модель не задана");
        }
        if (input == null || input.isBlank()) {
            throw new BadRequestException("Пустой текст для озвучки");
        }
        String format = responseFormat == null || responseFormat.isBlank() ? "mp3" : responseFormat;
        String voiceId = voice == null || voice.isBlank() ? "ara" : voice;
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", input);
        body.put("voice", voiceId);
        body.put("response_format", format);
        try {
            byte[] audio = webClient.post()
                    .uri(uri("/audio/speech"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(runtimeConfigService.getApiKey()))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (audio == null || audio.length == 0) {
                throw new BadRequestException("Пустой ответ TTS");
            }
            return audio;
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            if (detail == null || detail.isBlank()) {
                detail = e.getStatusCode() + " " + e.getStatusText();
            }
            // Gemini TTS accepts only pcm — keep PSY on Grok+mp3 by default.
            if (model.toLowerCase().contains("gemini") && model.toLowerCase().contains("tts")) {
                throw new BadRequestException(
                        "Модель " + model + " не совместима с текущим TTS (нужен pcm). "
                                + "Выберите x-ai/grok-voice-tts-1.0. Детали: " + detail);
            }
            throw new BadRequestException("TTS ошибка: " + detail);
        } catch (Exception e) {
            throw new BadRequestException("TTS ошибка: " + e.getMessage());
        }
    }

    public record TranscriptionResult(String text, Integer promptTokens, Integer completionTokens, Integer totalTokens, BigDecimal costRub) {
    }

    /**
     * OpenAI-compatible STT: {@code POST /audio/transcriptions} (multipart).
     * Does not record usage — caller meters.
     */
    public TranscriptionResult transcribe(byte[] audio, String filename, String contentType, String model, String language) {
        userBalanceService.requireLlmAllowed();
        ensureApiConfigured();
        if (model == null || model.isBlank()) {
            throw new BadRequestException("STT-модель не задана");
        }
        if (audio == null || audio.length == 0) {
            throw new BadRequestException("Пустой аудиофайл");
        }
        String name = (filename == null || filename.isBlank()) ? "voice.webm" : filename;
        MediaType partType;
        try {
            partType = contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            partType = MediaType.APPLICATION_OCTET_STREAM;
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        MediaType finalPartType = partType;
        builder.part("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return name;
            }
        }).contentType(finalPartType);
        builder.part("model", model);
        if (language != null && !language.isBlank()) {
            builder.part("language", language);
        }

        try {
            JsonNode response = webClient.post()
                    .uri(uri("/audio/transcriptions"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(h -> h.setBearerAuth(runtimeConfigService.getApiKey()))
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null) {
                throw new BadRequestException("Пустой ответ STT");
            }
            String text = response.path("text").asText("");
            if (text.isBlank()) {
                text = response.path("data").path("text").asText("");
            }
            if (text.isBlank()) {
                throw new BadRequestException("STT не вернул текст");
            }
            JsonNode usage = response.path("usage");
            Integer prompt = intOrNull(usage, "prompt_tokens");
            Integer completion = intOrNull(usage, "completion_tokens");
            Integer total = intOrNull(usage, "total_tokens");
            if (total == null && (prompt != null || completion != null)) {
                total = (prompt == null ? 0 : prompt) + (completion == null ? 0 : completion);
            }
            BigDecimal cost = firstDecimal(
                    usage.path("cost"),
                    usage.path("total_cost"),
                    response.path("cost"),
                    response.path("total_cost")
            );
            return new TranscriptionResult(text.trim(), prompt, completion, total, cost);
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            if (detail == null || detail.isBlank()) {
                detail = e.getStatusCode() + " " + e.getStatusText();
            }
            throw new BadRequestException("STT ошибка: " + detail);
        } catch (Exception e) {
            throw new BadRequestException("STT ошибка: " + e.getMessage());
        }
    }

    public float[] embed(String text) {
        // Embeddings are ancillary — do not re-check balance (main call already gated / charged).
        ensureApiConfigured();
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
        LlmCallContext parent = LlmUsageContext.get();
        LlmCallContext embedCtx = parent == null
                ? LlmCallContext.of("embed")
                : parent.withPurpose("embed");
        recordUsage(response, embeddingModel, embedCtx);
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

    private static LlmCallContext resolveContext(LlmCallContext context) {
        LlmCallContext current = LlmUsageContext.get();
        if (context == null) {
            return current != null ? current : LlmCallContext.of("chat");
        }
        if (current == null) {
            return context;
        }
        return new LlmCallContext(
                context.purpose() != null ? context.purpose() : current.purpose(),
                context.clientId() != null ? context.clientId() : current.clientId(),
                context.sessionId() != null ? context.sessionId() : current.sessionId(),
                context.refId() != null ? context.refId() : current.refId()
        );
    }

    private URI uri(String path) {
        return URI.create(runtimeConfigService.getBaseUrl() + path);
    }

    private void recordUsage(JsonNode response, String model, LlmCallContext context) {
        try {
            JsonNode usage = response.path("usage");
            Integer prompt = intOrNull(usage, "prompt_tokens");
            Integer completion = intOrNull(usage, "completion_tokens");
            Integer total = intOrNull(usage, "total_tokens");
            if (total == null && (prompt != null || completion != null)) {
                total = (prompt == null ? 0 : prompt) + (completion == null ? 0 : completion);
            }
            BigDecimal cost = firstDecimal(
                    usage.path("cost"),
                    usage.path("total_cost"),
                    response.path("cost"),
                    response.path("total_cost")
            );
            if (cost == null) {
                cost = estimateCostRub(model, prompt, completion, total);
            }
            String generationId = response.path("id").asText(null);
            LlmCallContext ctx = context == null ? LlmCallContext.of("chat") : context;
            llmUsageService.record(
                    model,
                    ctx.purpose(),
                    prompt,
                    completion,
                    total,
                    cost,
                    generationId,
                    ctx.clientId(),
                    ctx.sessionId(),
                    ctx.refId()
            );
        } catch (Exception ignored) {
            // usage logging must not break chat
        }
    }

    private BigDecimal estimateCostRub(String model, Integer prompt, Integer completion, Integer total) {
        try {
            BigDecimal promptPrice = routerAiClient.promptPricePerToken(model);
            if (promptPrice == null) {
                return null;
            }
            int promptTokens = prompt != null ? prompt : (total != null ? total : 0);
            int completionTokens = completion != null ? completion : 0;
            if (promptTokens <= 0 && completionTokens <= 0) {
                return null;
            }
            // RouterAI model pricing.prompt is ₽ per token; embeddings use prompt side only.
            BigDecimal cost = promptPrice.multiply(BigDecimal.valueOf(promptTokens));
            if (completionTokens > 0) {
                // Fall back to prompt price for completion if we don't look up completion price here.
                cost = cost.add(promptPrice.multiply(BigDecimal.valueOf(completionTokens)));
            }
            return cost;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer intOrNull(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        return n.isNumber() ? n.asInt() : null;
    }

    private static BigDecimal firstDecimal(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && n.isNumber()) {
                return BigDecimal.valueOf(n.asDouble());
            }
            if (n != null && n.isTextual()) {
                try {
                    return new BigDecimal(n.asText().trim());
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return null;
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
        userBalanceService.requireLlmAllowed();
        ensureApiConfigured();
    }

    private void ensureApiConfigured() {
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
