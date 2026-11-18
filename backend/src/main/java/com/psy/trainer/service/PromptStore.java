package com.psy.trainer.service;

import com.psy.trainer.config.PsyProperties;
import com.psy.trainer.web.dto.Dtos;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptStore {

    private static final Logger log = LoggerFactory.getLogger(PromptStore.class);

    private final S3Client s3Client;
    private final PsyProperties properties;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptStore(S3Client s3Client, PsyProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.getMinio().isEnabled()) {
            log.warn("MinIO disabled — prompts load from classpath only");
            return;
        }
        try {
            ensureBucket();
            for (String id : PromptCatalog.ids()) {
                if (!objectExists(id)) {
                    String seed = readClasspath(id);
                    putObject(id, seed);
                    log.info("Seeded prompt {} into MinIO", id);
                }
            }
        } catch (Exception e) {
            log.warn("MinIO init failed, falling back to classpath prompts: {}", e.getMessage());
        }
    }

    public String load(String id) {
        return cache.computeIfAbsent(id, this::loadFresh);
    }

    public List<Dtos.PromptSummary> list() {
        List<Dtos.PromptSummary> out = new ArrayList<>();
        for (String id : PromptCatalog.ids()) {
            out.add(new Dtos.PromptSummary(id, PromptCatalog.label(id), load(id).length()));
        }
        return out;
    }

    public Dtos.PromptDetail get(String id) {
        requireKnown(id);
        return new Dtos.PromptDetail(id, PromptCatalog.label(id), load(id));
    }

    public Dtos.PromptDetail save(String id, String content) {
        requireKnown(id);
        if (content == null) {
            throw new BadRequestException("Пустой промпт");
        }
        if (properties.getMinio().isEnabled()) {
            try {
                ensureBucket();
                putObject(id, content);
            } catch (Exception e) {
                throw new BadRequestException("Не удалось сохранить промпт в MinIO: " + e.getMessage());
            }
        }
        cache.put(id, content);
        return new Dtos.PromptDetail(id, PromptCatalog.label(id), content);
    }

    public void invalidate(String id) {
        cache.remove(id);
    }

    private String loadFresh(String id) {
        requireKnown(id);
        if (properties.getMinio().isEnabled()) {
            try {
                String fromMinio = getObject(id);
                if (fromMinio != null) {
                    return fromMinio;
                }
            } catch (Exception e) {
                log.warn("MinIO read failed for {}: {}", id, e.getMessage());
            }
        }
        return readClasspath(id);
    }

    private void requireKnown(String id) {
        if (!PromptCatalog.isKnown(id)) {
            throw new NotFoundException("Промпт не найден: " + id);
        }
    }

    private void ensureBucket() {
        String bucket = properties.getMinio().getBucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        }
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getMinio().getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private String getObject(String key) {
        try {
            var bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getMinio().getBucket())
                    .key(key)
                    .build());
            return bytes.asString(StandardCharsets.UTF_8);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private void putObject(String key, String content) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getMinio().getBucket())
                        .key(key)
                        .contentType("text/plain; charset=utf-8")
                        .build(),
                RequestBody.fromString(content, StandardCharsets.UTF_8)
        );
    }

    private String readClasspath(String id) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + id);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load prompt " + id, e);
        }
    }
}
