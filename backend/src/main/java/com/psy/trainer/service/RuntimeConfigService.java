package com.psy.trainer.service;

import com.psy.trainer.config.PsyProperties;
import com.psy.trainer.domain.AppSetting;
import com.psy.trainer.repo.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class RuntimeConfigService {

    public static final String KEY_LLM_MODEL = "llm.model";
    public static final String KEY_LLM_BASE_URL = "llm.baseUrl";
    public static final String KEY_LLM_API_KEY = "llm.apiKey";

    private final AppSettingRepository settingRepository;
    private final PsyProperties properties;
    private final AtomicReference<String> modelCache = new AtomicReference<>();
    private final AtomicReference<String> baseUrlCache = new AtomicReference<>();
    private final AtomicReference<String> apiKeyCache = new AtomicReference<>();

    public RuntimeConfigService(AppSettingRepository settingRepository, PsyProperties properties) {
        this.settingRepository = settingRepository;
        this.properties = properties;
    }

    public String getModel() {
        return cachedOrLoad(modelCache, KEY_LLM_MODEL, properties.getLlm().getModel());
    }

    public String getDefaultModel() {
        return properties.getLlm().getModel();
    }

    public String getBaseUrl() {
        return trimSlash(cachedOrLoad(baseUrlCache, KEY_LLM_BASE_URL, properties.getLlm().getBaseUrl()));
    }

    public String getDefaultBaseUrl() {
        return trimSlash(properties.getLlm().getBaseUrl());
    }

    public String getApiKey() {
        return cachedOrLoad(apiKeyCache, KEY_LLM_API_KEY, properties.getLlm().getApiKey());
    }

    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    public String maskedApiKey() {
        String key = getApiKey();
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "••••";
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4);
    }

    @Transactional
    public String setModel(String model) {
        if (model == null || model.isBlank()) {
            throw new BadRequestException("Модель не задана");
        }
        return persist(KEY_LLM_MODEL, model.trim(), modelCache);
    }

    @Transactional
    public void setLlmConnection(String baseUrl, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BadRequestException("Base URL не задан");
        }
        String normalizedUrl = trimSlash(baseUrl.trim());
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            throw new BadRequestException("Base URL должен начинаться с http:// или https://");
        }
        persist(KEY_LLM_BASE_URL, normalizedUrl, baseUrlCache);

        if (apiKey != null && !apiKey.isBlank()) {
            persist(KEY_LLM_API_KEY, apiKey.trim(), apiKeyCache);
        }
    }

    @Transactional
    public void clearApiKey() {
        settingRepository.findById(KEY_LLM_API_KEY).ifPresent(settingRepository::delete);
        apiKeyCache.set(null);
    }

    private String cachedOrLoad(AtomicReference<String> cache, String key, String fallback) {
        String cached = cache.get();
        if (cached != null) {
            return cached;
        }
        String resolved = settingRepository.findById(key)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback == null ? "" : fallback);
        cache.set(resolved);
        return resolved;
    }

    private String persist(String key, String value, AtomicReference<String> cache) {
        AppSetting setting = settingRepository.findById(key).orElseGet(() -> AppSetting.of(key, value));
        setting.updateValue(value);
        settingRepository.save(setting);
        cache.set(value);
        return value;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String t = url.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
