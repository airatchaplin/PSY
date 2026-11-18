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

    public List<ModelInfo> listModels() {
        ensureApiKey();
        JsonNode data = get("/models").path("data");
        List<ModelInfo> out = new ArrayList<>();
        if (!data.isArray()) {
            return out;
        }
        for (JsonNode n : data) {
            out.add(new ModelInfo(
                    n.path("id").asText(),
                    n.path("name").asText(n.path("id").asText()),
                    n.path("context_length").isNumber() ? n.path("context_length").asInt() : null
            ));
        }
        return out;
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

    public record ModelInfo(String id, String name, Integer contextLength) {
    }
}
