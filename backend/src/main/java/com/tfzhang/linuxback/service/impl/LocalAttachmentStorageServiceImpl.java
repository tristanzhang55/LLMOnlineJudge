package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.AttachmentException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.config.StorageProperties;
import com.tfzhang.linuxback.service.AttachmentStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalAttachmentStorageServiceImpl implements AttachmentStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    private final StorageProperties props;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private Path basePath;

    @PostConstruct
    void init() {
        basePath = Paths.get(props.getLocal().getBasePath()).toAbsolutePath().normalize();
    }

    @Override
    public String upload(MultipartFile file, String ownerType) {
        String contentType = file.getContentType();
        String ext = EXTENSIONS.get(contentType);
        if (ext == null) {
            throw new AttachmentException(
                    Msg.t("error.attachment.unsupportedType"), "UNSUPPORTED_FILE_TYPE");
        }

        LocalDate now = LocalDate.now();
        String storageKey = String.format(
                "%s/%04d/%02d/%s.%s",
                ownerType, now.getYear(), now.getMonthValue(), UUID.randomUUID(), ext);

        Path target = basePath.resolve(storageKey).normalize();
        if (!target.startsWith(basePath)) {
            throw new AttachmentException(Msg.t("error.attachment.storeFailed"), "STORAGE_ERROR");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new AttachmentException(Msg.t("error.attachment.storeFailed"), "STORAGE_ERROR");
        }

        return storageKey;
    }

    @Override
    public byte[] read(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new AttachmentException(Msg.t("error.attachment.readFailed"), "STORAGE_ERROR");
        }
        Path target = basePath.resolve(storageKey).normalize();
        if (!target.startsWith(basePath) || !Files.isRegularFile(target)) {
            throw new AttachmentException(Msg.t("error.attachment.readFailed"), "STORAGE_ERROR");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new AttachmentException(Msg.t("error.attachment.readFailed"), "STORAGE_ERROR");
        }
    }

    @Override
    public String resolveUrl(String storageKey) {
        return buildUrlPrefix() + storageKey;
    }

    @Override
    public Optional<String> extractStorageKey(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String prefix = buildUrlPrefix();
        if (!url.startsWith(prefix)) {
            return Optional.empty();
        }
        String key = url.substring(prefix.length());
        if (key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    private String buildUrlPrefix() {
        String ctx = contextPath == null ? "" : contextPath;
        String urlPrefix = props.getLocal().getUrlPrefix();
        if (!urlPrefix.startsWith("/")) {
            urlPrefix = "/" + urlPrefix;
        }
        if (urlPrefix.endsWith("/")) {
            urlPrefix = urlPrefix.substring(0, urlPrefix.length() - 1);
        }
        return ctx + urlPrefix + "/";
    }
}
