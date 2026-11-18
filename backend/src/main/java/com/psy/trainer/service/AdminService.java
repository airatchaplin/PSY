package com.psy.trainer.service;

import com.psy.trainer.keycloak.KeycloakAdminClient;
import com.psy.trainer.llm.RouterAiClient;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminService {

    private static final String ACTIVITY_URL = "https://routerai.ru/settings/activity";
    private static final String DOCS_URL = "https://routerai.ru/docs/reference";

    private final RouterAiClient routerAiClient;
    private final RuntimeConfigService runtimeConfigService;
    private final PromptStore promptStore;
    private final LlmUsageService llmUsageService;
    private final KeycloakAdminClient keycloakAdminClient;

    public AdminService(
            RouterAiClient routerAiClient,
            RuntimeConfigService runtimeConfigService,
            PromptStore promptStore,
            LlmUsageService llmUsageService,
            KeycloakAdminClient keycloakAdminClient
    ) {
        this.routerAiClient = routerAiClient;
        this.runtimeConfigService = runtimeConfigService;
        this.promptStore = promptStore;
        this.llmUsageService = llmUsageService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    public Dtos.AdminOverviewResponse overview() {
        BigDecimal balance = null;
        RouterAiClient.KeyInfo key = null;
        String billingError = null;

        if (!runtimeConfigService.hasApiKey()) {
            billingError = "API key не задан";
        } else {
            try {
                balance = routerAiClient.creditsBalance();
                key = routerAiClient.keyInfo();
            } catch (Exception e) {
                billingError = e.getMessage();
            }
        }

        return new Dtos.AdminOverviewResponse(
                balance,
                key == null ? null : key.name(),
                key == null ? null : key.usage(),
                key == null ? null : key.limit(),
                key == null ? null : key.limitRemaining(),
                key == null ? null : key.usageMonthly(),
                key == null ? null : key.limitReset(),
                key != null && key.disabled(),
                runtimeConfigService.getModel(),
                runtimeConfigService.getDefaultModel(),
                runtimeConfigService.getBaseUrl(),
                runtimeConfigService.getDefaultBaseUrl(),
                runtimeConfigService.maskedApiKey(),
                runtimeConfigService.hasApiKey(),
                billingError,
                ACTIVITY_URL,
                DOCS_URL
        );
    }

    public List<Dtos.AdminModelOption> models() {
        return routerAiClient.listModels().stream()
                .map(m -> new Dtos.AdminModelOption(m.id(), m.name(), m.contextLength()))
                .toList();
    }

    @Transactional
    public Dtos.AdminOverviewResponse updateModel(Dtos.UpdateModelRequest request) {
        runtimeConfigService.setModel(request == null ? null : request.model());
        return overview();
    }

    @Transactional
    public Dtos.AdminOverviewResponse updateConnection(Dtos.UpdateLlmConnectionRequest request) {
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        runtimeConfigService.setLlmConnection(request.baseUrl(), request.apiKey());
        return overview();
    }

    public List<Dtos.PromptSummary> prompts() {
        return promptStore.list();
    }

    public Dtos.PromptDetail prompt(String id) {
        return promptStore.get(id);
    }

    public Dtos.PromptDetail updatePrompt(String id, Dtos.UpdatePromptRequest request) {
        return promptStore.save(id, request == null ? null : request.content());
    }

    public Dtos.AdminUsageStatsResponse usage(int days) {
        return llmUsageService.stats(days);
    }

    public Dtos.AdminUsageStatsResponse usage(
            java.time.Instant from,
            java.time.Instant to,
            String model,
            String source
    ) {
        return llmUsageService.stats(from, to, model, source);
    }

    public List<Dtos.KeycloakUserResponse> listUsers() {
        return keycloakAdminClient.listUsers().stream()
                .map(u -> new Dtos.KeycloakUserResponse(
                        u.id(), u.username(), u.email(), u.firstName(), u.lastName(), u.enabled(), u.roles()
                ))
                .toList();
    }

    public Dtos.KeycloakUserResponse createUser(Dtos.CreateKeycloakUserRequest request) {
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        var created = keycloakAdminClient.createUser(
                request.username(),
                request.password(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.admin()
        );
        return new Dtos.KeycloakUserResponse(
                created.id(),
                created.username(),
                created.email(),
                created.firstName(),
                created.lastName(),
                created.enabled(),
                created.roles()
        );
    }
}
