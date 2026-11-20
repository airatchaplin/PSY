package com.psy.trainer.service;



import com.psy.trainer.domain.AppUser;

import com.psy.trainer.domain.BalanceTopUp;

import com.psy.trainer.repo.AppUserRepository;

import com.psy.trainer.repo.BalanceTopUpRepository;

import com.psy.trainer.web.dto.Dtos;

import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;

import org.springframework.transaction.annotation.Transactional;



import java.math.BigDecimal;

import java.math.RoundingMode;

import java.util.List;

import java.util.Optional;

import java.util.UUID;



@Service

public class UserBalanceService {



    private final AppUserRepository appUserRepository;

    private final BalanceTopUpRepository balanceTopUpRepository;



    public UserBalanceService(

            AppUserRepository appUserRepository,

            BalanceTopUpRepository balanceTopUpRepository

    ) {

        this.appUserRepository = appUserRepository;

        this.balanceTopUpRepository = balanceTopUpRepository;

    }



    /**
     * Block LLM when authenticated user balance is zero or negative. No JWT → allow (system jobs).
     * NOT_SUPPORTED: must not join the caller TX — otherwise a caught InsufficientBalanceException
     * (e.g. during embedding) marks the outer transaction rollback-only → HTTP 500.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public void requireLlmAllowed() {
        currentUserId().ifPresent(id -> {
            AppUser user = appUserRepository.findById(id).orElse(null);
            if (user != null && user.getBalanceRub().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InsufficientBalanceException(
                        "Недостаточно средств на балансе. Обратитесь к администратору для пополнения."
                );
            }
        });
    }



    @Transactional(readOnly = true)

    public BigDecimal balanceOf(UUID userId) {

        return appUserRepository.findById(userId)

                .map(AppUser::getBalanceRub)

                .orElse(BigDecimal.ZERO);

    }



    @Transactional(readOnly = true)

    public BigDecimal currentBalanceOrZero() {

        return currentUserId()

                .flatMap(appUserRepository::findById)

                .map(AppUser::getBalanceRub)

                .orElse(BigDecimal.ZERO);

    }



    @Transactional(readOnly = true)

    public BigDecimal totalUsersBalance() {

        BigDecimal sum = appUserRepository.sumBalanceRub();

        return sum == null ? BigDecimal.ZERO : sum;

    }



    /** Deduct LLM cost from the current authenticated user (best-effort). */

    @Transactional

    public void chargeCurrentUser(BigDecimal amountRub) {

        if (amountRub == null || amountRub.compareTo(BigDecimal.ZERO) <= 0) {

            return;

        }

        currentUserId().ifPresent(id -> appUserRepository.deductBalance(id, scale(amountRub)));

    }



    @Transactional

    public AppUser credit(UUID userId, BigDecimal amountRub) {

        if (amountRub == null || amountRub.compareTo(BigDecimal.ZERO) <= 0) {

            throw new BadRequestException("Сумма пополнения должна быть больше 0");

        }

        AppUser user = appUserRepository.findById(userId)

                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        BigDecimal credited = scale(amountRub);

        user.setBalanceRub(user.getBalanceRub().add(credited));

        AppUser saved = appUserRepository.save(user);

        AppUser createdBy = currentUserId().flatMap(appUserRepository::findById).orElse(null);

        balanceTopUpRepository.save(BalanceTopUp.of(saved, credited, saved.getBalanceRub(), createdBy));

        return saved;

    }



    @Transactional(readOnly = true)

    public List<Dtos.BalanceTopUpResponse> recentTopUps() {

        return balanceTopUpRepository.findTop100ByOrderByCreatedAtDesc().stream()

                .map(this::toDto)

                .toList();

    }



    @Transactional(readOnly = true)

    public List<Dtos.BalanceTopUpResponse> topUpsForUser(UUID userId) {

        return balanceTopUpRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()

                .map(this::toDto)

                .toList();

    }



    @Transactional(readOnly = true)

    public List<Dtos.BalanceTopUpResponse> currentUserTopUps() {

        UUID id = currentUserId().orElseThrow(() -> new UnauthorizedException("Требуется вход"));

        return topUpsForUser(id);

    }



    private Dtos.BalanceTopUpResponse toDto(BalanceTopUp row) {

        return new Dtos.BalanceTopUpResponse(

                row.getId().toString(),

                row.getUserId().toString(),

                row.getUsername(),

                row.getDisplayName(),

                row.getAmountRub(),

                row.getBalanceAfterRub(),

                row.getCreatedByUserId() == null ? null : row.getCreatedByUserId().toString(),

                row.getCreatedByUsername(),

                row.getCreatedAt()

        );

    }



    private Optional<UUID> currentUserId() {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {

                return Optional.empty();

            }

            String sub = jwt.getSubject();

            if (sub == null || sub.isBlank()) {

                return Optional.empty();

            }

            return Optional.of(UUID.fromString(sub));

        } catch (Exception ignored) {

            return Optional.empty();

        }

    }



    private static BigDecimal scale(BigDecimal value) {

        return value.setScale(4, RoundingMode.HALF_UP);

    }

}

