package com.psy.trainer.llm;

import java.util.function.Supplier;

/**
 * Request-scoped usage metadata for nested LLM calls (e.g. embeddings inside chat).
 * Blocking calls only — do not rely on this across Reactor threads without capturing the value.
 */
public final class LlmUsageContext {

    private static final ThreadLocal<LlmCallContext> HOLDER = new ThreadLocal<>();

    private LlmUsageContext() {
    }

    public static LlmCallContext get() {
        return HOLDER.get();
    }

    public static <T> T call(LlmCallContext ctx, Supplier<T> action) {
        LlmCallContext previous = HOLDER.get();
        HOLDER.set(ctx);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }

    public static void run(LlmCallContext ctx, Runnable action) {
        call(ctx, () -> {
            action.run();
            return null;
        });
    }
}
