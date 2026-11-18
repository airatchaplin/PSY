package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "therapist_profile")
public class TherapistProfile {

    @Id
    private UUID id;

    @Column(name = "auth_sub", nullable = false, unique = true, length = 128)
    private String authSub;

    @Column(name = "display_name")
    private String displayName;

    private String email;

    @Column(name = "disclaimer_accepted_at")
    private Instant disclaimerAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TherapistProfile create(String authSub, String displayName, String email) {
        TherapistProfile p = new TherapistProfile();
        p.id = UUID.randomUUID();
        p.authSub = authSub;
        p.displayName = displayName;
        p.email = email;
        p.createdAt = Instant.now();
        return p;
    }

    public UUID getId() {
        return id;
    }

    public String getAuthSub() {
        return authSub;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getDisclaimerAcceptedAt() {
        return disclaimerAcceptedAt;
    }

    public void acceptDisclaimer() {
        this.disclaimerAcceptedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
