package com.psy.trainer.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.psy.trainer.config.PsyProperties;
import com.psy.trainer.service.BadRequestException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {

    private final WebClient webClient;
    private final PsyProperties properties;

    public KeycloakAdminClient(WebClient.Builder builder, PsyProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    public List<KeycloakUser> listUsers() {
        String token = adminToken();
        String realm = properties.getKeycloak().getRealm();
        JsonNode users = get(token, "/admin/realms/" + realm + "/users?max=200");
        List<KeycloakUser> out = new ArrayList<>();
        if (users == null || !users.isArray()) {
            return out;
        }
        for (JsonNode u : users) {
            String id = u.path("id").asText();
            List<String> roles = listRealmRoles(token, realm, id);
            out.add(new KeycloakUser(
                    id,
                    u.path("username").asText(""),
                    u.path("email").asText(null),
                    u.path("firstName").asText(null),
                    u.path("lastName").asText(null),
                    u.path("enabled").asBoolean(true),
                    roles
            ));
        }
        return out;
    }

    public KeycloakUser createUser(
            String username,
            String password,
            String email,
            String firstName,
            String lastName,
            boolean admin
    ) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("Укажите логин");
        }
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Укажите пароль");
        }
        String token = adminToken();
        String realm = properties.getKeycloak().getRealm();

        Map<String, Object> body = new HashMap<>();
        body.put("username", username.trim());
        body.put("enabled", true);
        body.put("emailVerified", true);
        if (email != null && !email.isBlank()) {
            body.put("email", email.trim());
        }
        if (firstName != null && !firstName.isBlank()) {
            body.put("firstName", firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            body.put("lastName", lastName.trim());
        }

        String userId;
        try {
            ResponseEntity<Void> created = webClient.post()
                    .uri(base() + "/admin/realms/" + realm + "/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            userId = extractUserId(created);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                throw new BadRequestException("Пользователь уже существует");
            }
            throw new BadRequestException("Keycloak create user: HTTP " + e.getStatusCode().value()
                    + " " + e.getResponseBodyAsString());
        }

        if (userId == null || userId.isBlank()) {
            userId = findUserIdByUsername(token, realm, username.trim());
        }
        if (userId == null) {
            throw new BadRequestException("Пользователь создан, но id не найден");
        }

        setPassword(token, realm, userId, password);
        assignRealmRole(token, realm, userId, "therapist");
        if (admin) {
            assignRealmRole(token, realm, userId, "admin");
        }

        List<String> roles = listRealmRoles(token, realm, userId);
        return new KeycloakUser(
                userId,
                username.trim(),
                blankToNull(email),
                blankToNull(firstName),
                blankToNull(lastName),
                true,
                roles
        );
    }

    private void setPassword(String token, String realm, String userId, String password) {
        Map<String, Object> cred = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );
        try {
            webClient.put()
                    .uri(base() + "/admin/realms/" + realm + "/users/" + userId + "/reset-password")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(cred)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw new BadRequestException("Не удалось задать пароль: HTTP " + e.getStatusCode().value());
        }
    }

    private void assignRealmRole(String token, String realm, String userId, String roleName) {
        JsonNode role = get(token, "/admin/realms/" + realm + "/roles/" + roleName);
        if (role == null || role.path("name").isMissingNode()) {
            throw new BadRequestException("Роль не найдена в Keycloak: " + roleName);
        }
        try {
            webClient.post()
                    .uri(base() + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(List.of(role))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw new BadRequestException("Не удалось назначить роль " + roleName + ": HTTP " + e.getStatusCode().value());
        }
    }

    private List<String> listRealmRoles(String token, String realm, String userId) {
        try {
            JsonNode roles = get(token, "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm");
            List<String> out = new ArrayList<>();
            if (roles != null && roles.isArray()) {
                for (JsonNode r : roles) {
                    String name = r.path("name").asText(null);
                    if (name != null && !name.isBlank()) {
                        out.add(name);
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String findUserIdByUsername(String token, String realm, String username) {
        JsonNode users = get(token, "/admin/realms/" + realm + "/users?username=" + username + "&exact=true");
        if (users != null && users.isArray() && !users.isEmpty()) {
            return users.get(0).path("id").asText(null);
        }
        return null;
    }

    private String adminToken() {
        PsyProperties.Keycloak kc = properties.getKeycloak();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", kc.getAdminUsername());
        form.add("password", kc.getAdminPassword());
        try {
            JsonNode token = webClient.post()
                    .uri(base() + "/realms/master/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (token == null || token.path("access_token").asText("").isBlank()) {
                throw new BadRequestException("Не удалось получить токен Keycloak admin");
            }
            return token.path("access_token").asText();
        } catch (WebClientResponseException e) {
            throw new BadRequestException("Keycloak admin login failed: HTTP " + e.getStatusCode().value());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Keycloak admin login failed: " + e.getMessage());
        }
    }

    private JsonNode get(String token, String path) {
        try {
            return webClient.get()
                    .uri(base() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new BadRequestException("Keycloak GET " + path + ": HTTP " + e.getStatusCode().value()
                    + " " + e.getResponseBodyAsString());
        }
    }

    private String base() {
        String url = properties.getKeycloak().getAdminServerUrl();
        if (url == null || url.isBlank()) {
            return "http://localhost:8082";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url.trim();
    }

    private static String extractUserId(ResponseEntity<Void> created) {
        if (created == null || created.getHeaders().getLocation() == null) {
            return null;
        }
        String path = created.getHeaders().getLocation().getPath();
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public record KeycloakUser(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            List<String> roles
    ) {
    }
}
