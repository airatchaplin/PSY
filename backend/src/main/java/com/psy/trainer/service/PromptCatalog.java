package com.psy.trainer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PromptCatalog {
    private PromptCatalog() {
    }

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("client-system.txt", "Клиент (system)");
        LABELS.put("supervisor-system.txt", "Супервизор (system)");
        LABELS.put("analysis-system.txt", "Анализ сеанса");
        LABELS.put("case-generation.txt", "Генерация кейса");
        LABELS.put("persona-generation.txt", "Генерация персоны");
        LABELS.put("session-return.txt", "Возврат клиента на сеанс");
        LABELS.put("therapy-progress.txt", "Оценка прогресса терапии");
    }

    public static List<String> ids() {
        return List.copyOf(LABELS.keySet());
    }

    public static String label(String id) {
        return LABELS.getOrDefault(id, id);
    }

    public static boolean isKnown(String id) {
        return LABELS.containsKey(id);
    }
}
