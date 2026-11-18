package com.psy.trainer.repo;

import com.psy.trainer.domain.TherapistProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TherapistProfileRepository extends JpaRepository<TherapistProfile, UUID> {
    Optional<TherapistProfile> findByAuthSub(String authSub);

    java.util.List<TherapistProfile> findByIdNotOrderByDisplayNameAsc(UUID id);
}
