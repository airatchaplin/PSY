package com.psy.trainer.llm;

import java.util.UUID;

/** Metadata attached to an LLM call for usage metering. */
public record LlmCallContext(
        String purpose,
        UUID clientId,
        UUID sessionId,
        UUID refId
) {
    public static LlmCallContext of(String purpose) {
        return new LlmCallContext(purpose, null, null, null);
    }

    public static LlmCallContext of(String purpose, UUID clientId, UUID sessionId) {
        return new LlmCallContext(purpose, clientId, sessionId, null);
    }

    public static LlmCallContext of(String purpose, UUID clientId, UUID sessionId, UUID refId) {
        return new LlmCallContext(purpose, clientId, sessionId, refId);
    }

    public LlmCallContext withPurpose(String nextPurpose) {
        return new LlmCallContext(nextPurpose, clientId, sessionId, refId);
    }
}
