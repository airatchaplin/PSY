package com.psy.trainer.domain;

public enum AppRole {
    THERAPIST,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public static AppRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty role");
        }
        String n = raw.trim().toUpperCase();
        if (n.startsWith("ROLE_")) {
            n = n.substring(5);
        }
        return AppRole.valueOf(n);
    }
}
