package com.psy.trainer.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.psy.trainer.config.PsyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class QdrantClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

    private final WebClient webClient;
    private final PsyProperties properties;
    private final ObjectMapper objectMapper;

    public QdrantClient(WebClient.Builder builder, PsyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder.baseUrl(trimSlash(properties.getQdrant().getUrl())).build();
    }

    public void ensureCollections() {
        ensureCollection(properties.getQdrant().getClientMemoryCollection());
        ensureCollection(properties.getQdrant().getGestaltKnowledgeCollection());
    }

    private void ensureCollection(String name) {
        try {
            webClient.put()
                    .uri("/collections/{name}", name)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "vectors", Map.of(
                                    "size", properties.getQdrant().getVectorSize(),
                                    "distance", "Cosine"
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.debug("Collection {} ensure: {}", name, e.getMessage());
        }
    }

    public void upsert(String collection, UUID pointId, float[] vector, Map<String, Object> payload) {
        ObjectNode point = objectMapper.createObjectNode();
        point.put("id", pointId.toString());
        ArrayNode vec = point.putArray("vector");
        for (float v : vector) {
            vec.add(v);
        }
        point.set("payload", objectMapper.valueToTree(payload));

        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("points").add(point);

        webClient.put()
                .uri("/collections/{name}/points?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public List<ScoredPoint> search(String collection, float[] vector, int limit, Map<String, Object> mustMatch) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode vec = body.putArray("vector");
        for (float v : vector) {
            vec.add(v);
        }
        body.put("limit", limit);
        body.put("with_payload", true);

        if (mustMatch != null && !mustMatch.isEmpty()) {
            ObjectNode filterNode = body.putObject("filter");
            ArrayNode mustArr = filterNode.putArray("must");
            mustMatch.forEach((k, v) -> {
                ObjectNode item = mustArr.addObject();
                item.put("key", k);
                ObjectNode match = item.putObject("match");
                if (v instanceof Number n) {
                    match.put("value", n.doubleValue());
                } else {
                    match.put("value", String.valueOf(v));
                }
            });
        }

        try {
            JsonNode response = webClient.post()
                    .uri("/collections/{name}/points/search", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            List<ScoredPoint> result = new ArrayList<>();
            if (response == null) {
                return result;
            }
            for (JsonNode hit : response.path("result")) {
                String text = hit.path("payload").path("text").asText("");
                double score = hit.path("score").asDouble();
                result.add(new ScoredPoint(text, score, hit.path("payload")));
            }
            return result;
        } catch (Exception e) {
            log.warn("Qdrant search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record ScoredPoint(String text, double score, JsonNode payload) {
    }
}
