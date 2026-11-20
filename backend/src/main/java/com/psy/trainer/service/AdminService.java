package com.psy.trainer.service;

import com.psy.trainer.domain.AppRole;
import com.psy.trainer.domain.AppUser;
import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.llm.RouterAiClient;
import com.psy.trainer.repo.AppUserRepository;
import com.psy.trainer.repo.TherapistProfileRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private static final String ACTIVITY_URL = "https://routerai.ru/settings/activity";
    private static final String DOCS_URL = "https://routerai.ru/docs/reference";

    private final RouterAiClient routerAiClient;
    private final RuntimeConfigService runtimeConfigService;
    private final PromptStore promptStore;
    private final LlmUsageService llmUsageService;
    private final AppUserRepository appUserRepository;
    private final TherapistProfileRepository therapistProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserBalanceService userBalanceService;

    public AdminService(
            RouterAiClient routerAiClient,
            RuntimeConfigService runtimeConfigService,
            PromptStore promptStore,
            LlmUsageService llmUsageService,
            AppUserRepository appUserRepository,
            TherapistProfileRepository therapistProfileRepository,
            PasswordEncoder passwordEncoder,
            UserBalanceService userBalanceService
    ) {
        this.routerAiClient = routerAiClient;
        this.runtimeConfigService = runtimeConfigService;
        this.promptStore = promptStore;
        this.llmUsageService = llmUsageService;
        this.appUserRepository = appUserRepository;
        this.therapistProfileRepository = therapistProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.userBalanceService = userBalanceService;
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
                runtimeConfigService.getBaseUrl(),
                runtimeConfigService.getEmbeddingModel(),
                runtimeConfigService.maskedApiKey(),
                runtimeConfigService.hasApiKey(),
                billingError,
                ACTIVITY_URL,
                DOCS_URL
        );
    }

    public List<Dtos.AdminModelOption> models() {
        return routerAiClient.listModels().stream()
                .map(m -> new Dtos.AdminModelOption(
                        m.id(),
                        m.name(),
                        m.contextLength(),
                        per1m(m.promptPricePerToken()),
                        per1m(m.completionPricePerToken())
                ))
                .toList();
    }

    private static BigDecimal per1m(BigDecimal perToken) {
        if (perToken == null) {
            return null;
        }
        return perToken.multiply(BigDecimal.valueOf(1_000_000L));
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
        runtimeConfigService.setLlmConnection(request.baseUrl(), request.apiKey(), request.embeddingModel());
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
        return usage(from, to, model, source, null);
    }

    public Dtos.AdminUsageStatsResponse usage(
            java.time.Instant from,
            java.time.Instant to,
            String model,
            String source,
            java.util.UUID therapistId
    ) {
        if (therapistId != null) {
            return llmUsageService.statsForTherapist(resolveTherapistProfileId(therapistId), from, to, model, source);
        }
        return llmUsageService.stats(from, to, model, source);
    }

    /**
     * Admin UI may pass either {@code therapist_profile.id} (from usage.byUser)
     * or {@code app_user.id} (from /admin/users). LLM usage is keyed by profile id;
     * JWT {@code sub} is app_user.id and becomes therapist_profile.auth_sub.
     */
    private UUID resolveTherapistProfileId(UUID id) {
        if (therapistProfileRepository.findById(id).isPresent()) {
            return id;
        }
        var byAuthSub = therapistProfileRepository.findByAuthSub(id.toString());
        if (byAuthSub.isPresent()) {
            return byAuthSub.get().getId();
        }
        return appUserRepository.findById(id).flatMap(user -> {
            var byUserId = therapistProfileRepository.findByAuthSub(user.getId().toString());
            if (byUserId.isPresent()) {
                return byUserId;
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                return therapistProfileRepository.findByAuthSub(user.getUsername());
            }
            return java.util.Optional.<TherapistProfile>empty();
        }).map(TherapistProfile::getId).orElse(id);
    }

    @Transactional(readOnly = true)
    public List<Dtos.AppUserResponse> listUsers() {
        return appUserRepository.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional
    public Dtos.AppUserResponse createUser(Dtos.CreateAppUserRequest request) {
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new BadRequestException("Укажите логин");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("Укажите пароль");
        }
        String username = request.username().trim();
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Пользователь уже существует");
        }
        EnumSet<AppRole> roles = EnumSet.of(AppRole.THERAPIST);
        if (request.admin()) {
            roles.add(AppRole.ADMIN);
        }
        String display = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName().trim()
                : username;
        AppUser created = appUserRepository.save(AppUser.create(
                username,
                passwordEncoder.encode(request.password()),
                display,
                blankToNull(request.email()),
                roles
        ));
        return toUserResponse(created);
    }

    @Transactional
    public Dtos.AppUserResponse topUpUser(UUID userId, Dtos.TopUpBalanceRequest request) {
        if (userId == null) {
            throw new BadRequestException("Не указан пользователь");
        }
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        AppUser updated = userBalanceService.credit(userId, request.amountRub());
        return toUserResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<Dtos.BalanceTopUpResponse> topUpHistory() {
        return userBalanceService.recentTopUps();
    }

    @Transactional(readOnly = true)
    public List<Dtos.BalanceTopUpResponse> userTopUpHistory(UUID userId) {
        if (userId == null) {
            throw new BadRequestException("Не указан пользователь");
        }
        if (!appUserRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }
        return userBalanceService.topUpsForUser(userId);
    }

    private Dtos.AppUserResponse toUserResponse(AppUser u) {
        return new Dtos.AppUserResponse(
                u.getId().toString(),
                u.getUsername(),
                u.getEmail(),
                u.getDisplayName(),
                u.isEnabled(),
                u.roleSet().stream().map(Enum::name).sorted().toList(),
                u.getBalanceRub()
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
