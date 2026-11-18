package com.psy.trainer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(length = 256)
    private String email;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> roles = new ArrayList<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AppUser create(
            String username,
            String passwordHash,
            String displayName,
            String email,
            Set<AppRole> roles
    ) {
        AppUser u = new AppUser();
        u.id = UUID.randomUUID();
        u.username = username.trim().toLowerCase();
        u.passwordHash = passwordHash;
        u.displayName = displayName;
        u.email = email;
        u.roles = roles.stream().map(Enum::name).sorted().toList();
        u.enabled = true;
        u.createdAt = Instant.now();
        return u;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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

    public Set<AppRole> roleSet() {
        LinkedHashSet<AppRole> out = new LinkedHashSet<>();
        if (roles != null) {
            for (String r : roles) {
                out.add(AppRole.from(r));
            }
        }
        if (out.isEmpty()) {
            out.add(AppRole.THERAPIST);
        }
        return out;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<AppRole> roleSet) {
        this.roles = roleSet.stream().map(Enum::name).sorted().toList();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean hasRole(AppRole role) {
        return roleSet().contains(role);
    }
}
