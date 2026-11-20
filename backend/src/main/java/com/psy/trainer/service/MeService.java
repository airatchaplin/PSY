package com.psy.trainer.service;

import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.llm.RouterAiClient;
import com.psy.trainer.repo.TherapistProfileRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class MeService {

    private final CurrentTherapistService currentTherapistService;
    private final TherapistProfileRepository repository;
    private final UserBalanceService userBalanceService;
    private final RuntimeConfigService runtimeConfigService;
    private final RouterAiClient routerAiClient;
    private final LlmUsageService llmUsageService;

    public MeService(
            CurrentTherapistService currentTherapistService,
            TherapistProfileRepository repository,
            UserBalanceService userBalanceService,
            RuntimeConfigService runtimeConfigService,
            RouterAiClient routerAiClient,
            LlmUsageService llmUsageService
    ) {
        this.currentTherapistService = currentTherapistService;
        this.repository = repository;
        this.userBalanceService = userBalanceService;
        this.runtimeConfigService = runtimeConfigService;
        this.routerAiClient = routerAiClient;
        this.llmUsageService = llmUsageService;
    }

    @Transactional
    public Dtos.MeResponse me() {
        TherapistProfile p = currentTherapistService.requireCurrent();
        return toDto(p);
    }

    @Transactional
    public Dtos.MeResponse acceptDisclaimer() {
        TherapistProfile p = currentTherapistService.requireCurrent();
        p.acceptDisclaimer();
        return toDto(repository.save(p));
    }

    @Transactional(readOnly = true)
    public List<Dtos.BalanceTopUpResponse> balanceHistory() {
        return userBalanceService.currentUserTopUps();
    }

    @Transactional(readOnly = true)
    public Dtos.AdminUsageStatsResponse usage(
            java.time.Instant from,
            java.time.Instant to,
            String model,
            String source
    ) {
        TherapistProfile p = currentTherapistService.requireCurrent();
        return llmUsageService.statsForTherapist(p.getId(), from, to, model, source);
    }

    private Dtos.MeResponse toDto(TherapistProfile p) {
        List<String> roles = currentRoles();
        boolean admin = roles.stream().anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"));
        BigDecimal balance = balanceFor(p);
        BigDecimal appBalance = null;
        BigDecimal usersTotal = null;
        if (admin) {
            appBalance = loadAppBalance();
            usersTotal = userBalanceService.totalUsersBalance();
        }
        return new Dtos.MeResponse(
                p.getId(),
                p.getDisplayName(),
                p.getEmail(),
                p.getDisclaimerAcceptedAt() != null,
                p.getDisclaimerAcceptedAt(),
                admin,
                roles.stream().map(r -> r.startsWith("ROLE_") ? r.substring(5).toLowerCase() : r.toLowerCase()).toList(),
                balance,
                balance.compareTo(BigDecimal.ZERO) > 0,
                appBalance,
                usersTotal
        );
    }

    private BigDecimal loadAppBalance() {
        if (!runtimeConfigService.hasApiKey()) {
            return null;
        }
        try {
            return routerAiClient.creditsBalance();
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal balanceFor(TherapistProfile p) {
        try {
            return userBalanceService.balanceOf(UUID.fromString(p.getAuthSub()));
        } catch (Exception ignored) {
            return userBalanceService.currentBalanceOrZero();
        }
    }

    private static List<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return List.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
