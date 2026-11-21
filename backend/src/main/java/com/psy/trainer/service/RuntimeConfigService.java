package com.psy.trainer.service;

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
    public static final String KEY_LLM_EMBEDDING_MODEL = "llm.embeddingModel";
    public static final String KEY_TTS_ENABLED = "tts.enabled";
    public static final String KEY_TTS_MODEL = "tts.model";
    public static final String DEFAULT_TTS_MODEL = "x-ai/grok-voice-tts-1.0";
    public static final String KEY_STT_ENABLED = "stt.enabled";
    public static final String KEY_STT_MODEL = "stt.model";
    public static final String DEFAULT_STT_MODEL = "openai/whisper-large-v3";

    private final AppSettingRepository settingRepository;
    private final AtomicReference<String> modelCache = new AtomicReference<>();
    private final AtomicReference<String> baseUrlCache = new AtomicReference<>();
    private final AtomicReference<String> apiKeyCache = new AtomicReference<>();
    private final AtomicReference<String> embeddingModelCache = new AtomicReference<>();
    private final AtomicReference<String> ttsEnabledCache = new AtomicReference<>();
    private final AtomicReference<String> ttsModelCache = new AtomicReference<>();
    private final AtomicReference<String> sttEnabledCache = new AtomicReference<>();
    private final AtomicReference<String> sttModelCache = new AtomicReference<>();

    public RuntimeConfigService(AppSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public String getModel() {
        return cachedOrLoad(modelCache, KEY_LLM_MODEL);
    }

    public String getBaseUrl() {
        return trimSlash(cachedOrLoad(baseUrlCache, KEY_LLM_BASE_URL));
    }

    public String getApiKey() {
        return cachedOrLoad(apiKeyCache, KEY_LLM_API_KEY);
    }

    public String getEmbeddingModel() {
        return cachedOrLoad(embeddingModelCache, KEY_LLM_EMBEDDING_MODEL);
    }

    /** TTS is always available to users; they toggle it in profile. Admin only picks the model. */
    public boolean isTtsEnabled() {
        return true;
    }

    public String getTtsModel() {
        String model = cachedOrLoad(ttsModelCache, KEY_TTS_MODEL);
        return model == null || model.isBlank() ? DEFAULT_TTS_MODEL : model;
    }

    /** STT is always available to users; they toggle it in profile. Admin only picks the model. */
    public boolean isSttEnabled() {
        return true;
    }

    public String getSttModel() {
        String model = cachedOrLoad(sttModelCache, KEY_STT_MODEL);
        return model == null || model.isBlank() ? DEFAULT_STT_MODEL : model;
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
    public void setLlmConnection(String baseUrl, String apiKey, String embeddingModel) {
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

        if (embeddingModel != null && !embeddingModel.isBlank()) {
            persist(KEY_LLM_EMBEDDING_MODEL, embeddingModel.trim(), embeddingModelCache);
        } else if (getEmbeddingModel().isBlank()) {
            throw new BadRequestException("Embedding-модель не задана");
        }
    }

    @Transactional
    public void clearApiKey() {
        settingRepository.findById(KEY_LLM_API_KEY).ifPresent(settingRepository::delete);
        apiKeyCache.set("");
    }

    @Transactional
    public void setTtsSettings(boolean ignoredEnabled, String model) {
        persist(KEY_TTS_ENABLED, "true", ttsEnabledCache);
        if (model != null && !model.isBlank()) {
            persist(KEY_TTS_MODEL, model.trim(), ttsModelCache);
        } else if (getTtsModel().isBlank()) {
            persist(KEY_TTS_MODEL, DEFAULT_TTS_MODEL, ttsModelCache);
        }
    }

    @Transactional
    public void setSttSettings(boolean ignoredEnabled, String model) {
        persist(KEY_STT_ENABLED, "true", sttEnabledCache);
        if (model != null && !model.isBlank()) {
            persist(KEY_STT_MODEL, model.trim(), sttModelCache);
        } else if (getSttModel().isBlank()) {
            persist(KEY_STT_MODEL, DEFAULT_STT_MODEL, sttModelCache);
        }
    }

    private String cachedOrLoad(AtomicReference<String> cache, String key) {
        String cached = cache.get();
        if (cached != null) {
            return cached;
        }
        String resolved = settingRepository.findById(key)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse("");
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
