package com.psy.trainer.domain;

public enum CaseSource {
    BUILTIN,
    MANUAL,
    AI_PROMPT,
    AI_AUTO,
    /** Copy received from another therapist via share. */
    SHARED_COPY
}
