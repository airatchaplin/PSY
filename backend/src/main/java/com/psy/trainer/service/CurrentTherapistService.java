package com.psy.trainer.service;

import com.psy.trainer.domain.TherapistProfile;
import com.psy.trainer.repo.TherapistProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentTherapistService {

    private final TherapistProfileRepository repository;

    public CurrentTherapistService(TherapistProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TherapistProfile requireCurrent() {
        return findCurrent().orElseThrow(() -> new BadRequestException("Не авторизован"));
    }

    /** Best-effort current therapist for usage metering (null-safe, no throw). */
    @Transactional
    public java.util.Optional<TherapistProfile> findCurrent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return java.util.Optional.empty();
        }
        String sub = jwt.getSubject();
        return java.util.Optional.of(repository.findByAuthSub(sub).orElseGet(() -> {
            String name = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"), "Therapist");
            String email = jwt.getClaimAsString("email");
            return repository.save(TherapistProfile.create(sub, name, email));
        }));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
