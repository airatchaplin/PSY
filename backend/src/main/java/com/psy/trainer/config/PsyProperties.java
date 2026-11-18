package com.psy.trainer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "psy")
public class PsyProperties {

    private List<String> corsOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://localhost",
            "http://127.0.0.1:5173",
            "http://127.0.0.1"
    ));
    private final Llm llm = new Llm();
    private final Qdrant qdrant = new Qdrant();
    private final Minio minio = new Minio();
    private final Keycloak keycloak = new Keycloak();
    private String knowledgePath = "./docs/knowledge";

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        if (corsOrigins == null) {
            return;
        }
        // Env often arrives as a single comma-separated value.
        if (corsOrigins.size() == 1 && corsOrigins.getFirst().contains(",")) {
            this.corsOrigins = java.util.Arrays.stream(corsOrigins.getFirst().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            return;
        }
        this.corsOrigins = corsOrigins;
    }

    public Llm getLlm() {
        return llm;
    }

    public Qdrant getQdrant() {
        return qdrant;
    }

    public Minio getMinio() {
        return minio;
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public String getKnowledgePath() {
        return knowledgePath;
    }

    public void setKnowledgePath(String knowledgePath) {
        this.knowledgePath = knowledgePath;
    }

    public static class Llm {
        private String baseUrl;
        private String apiKey;
        private String model;
        private String embeddingModel;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }

    public static class Qdrant {
        private String url;
        private String clientMemoryCollection = "client_memory";
        private String gestaltKnowledgeCollection = "gestalt_knowledge";
        private int vectorSize = 1536;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getClientMemoryCollection() {
            return clientMemoryCollection;
        }

        public void setClientMemoryCollection(String clientMemoryCollection) {
            this.clientMemoryCollection = clientMemoryCollection;
        }

        public String getGestaltKnowledgeCollection() {
            return gestaltKnowledgeCollection;
        }

        public void setGestaltKnowledgeCollection(String gestaltKnowledgeCollection) {
            this.gestaltKnowledgeCollection = gestaltKnowledgeCollection;
        }

        public int getVectorSize() {
            return vectorSize;
        }

        public void setVectorSize(int vectorSize) {
            this.vectorSize = vectorSize;
        }
    }

    public static class Minio {
        private boolean enabled = true;
        private String endpoint = "http://localhost:9000";
        private String accessKey = "psy";
        private String secretKey = "psysecret";
        private String bucket = "psy-prompts";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class Keycloak {
        private String adminServerUrl = "http://localhost:8082";
        private String realm = "psy";
        private String adminUsername = "admin";
        private String adminPassword = "admin";

        public String getAdminServerUrl() {
            return adminServerUrl;
        }

        public void setAdminServerUrl(String adminServerUrl) {
            this.adminServerUrl = adminServerUrl;
        }

        public String getRealm() {
            return realm;
        }

        public void setRealm(String realm) {
            this.realm = realm;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }
}
