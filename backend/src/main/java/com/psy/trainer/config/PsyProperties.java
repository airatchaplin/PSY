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
    private final Qdrant qdrant = new Qdrant();
    private final Minio minio = new Minio();
    private final Jwt jwt = new Jwt();
    private final AuthSeed authSeed = new AuthSeed();
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

    public Qdrant getQdrant() {
        return qdrant;
    }

    public Minio getMinio() {
        return minio;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public AuthSeed getAuthSeed() {
        return authSeed;
    }

    public String getKnowledgePath() {
        return knowledgePath;
    }

    public void setKnowledgePath(String knowledgePath) {
        this.knowledgePath = knowledgePath;
    }

    public static class Jwt {
        private String secret = "change-me-psy-jwt-secret-min-32-chars!!";
        private String issuer = "psy-trainer";
        private long ttlSeconds = 28800;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class AuthSeed {
        private String adminUsername = "admin";
        private String adminPassword = "change-me-strong-admin";

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
}
