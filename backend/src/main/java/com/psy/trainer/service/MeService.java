package com.psy.trainer.service;

import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.repo.TherapistProfileRepository;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MeService {

    private final CurrentTherapistService currentTherapistService;
    private final TherapistProfileRepository repository;

    public MeService(CurrentTherapistService currentTherapistService, TherapistProfileRepository repository) {
        this.currentTherapistService = currentTherapistService;
        this.repository = repository;
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

    private static Dtos.MeResponse toDto(TherapistProfile p) {
        List<String> roles = currentRoles();
        boolean admin = roles.stream().anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"));
        return new Dtos.MeResponse(
                p.getId(),
                p.getDisplayName(),
                p.getEmail(),
                p.getDisclaimerAcceptedAt() != null,
                p.getDisclaimerAcceptedAt(),
                admin,
                roles.stream().map(r -> r.startsWith("ROLE_") ? r.substring(5).toLowerCase() : r.toLowerCase()).toList()
        );
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
