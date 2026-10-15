package com.tfzhang.linuxback.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.tfzhang.linuxback.common.AttachmentException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.config.StorageProperties;
import com.tfzhang.linuxback.service.AttachmentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "tencent-cos")
@RequiredArgsConstructor
public class TencentCosAttachmentStorageServiceImpl implements AttachmentStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp"
    );
    private static final Set<String> TEACHER_OWNER_TYPES = Set.of(
            "question",
            "question_answer",
            "sub_question",
            "sub_question_answer"
    );

    private final StorageProperties props;
    private final COSClient cosClient;

    @Override
    public String upload(MultipartFile file, String ownerType) {
        String contentType = file.getContentType();
        String ext = EXTENSIONS.get(contentType);
        if (ext == null) {
            throw new AttachmentException(
                    Msg.t("error.attachment.unsupportedType"), "UNSUPPORTED_FILE_TYPE");
        }
        String portal = portalFor(ownerType);
        LocalDate now = LocalDate.now();
        String storageKey = String.format(
                "%s/%s/%04d/%02d/%s.%s",
                portal, ownerType, now.getYear(), now.getMonthValue(), UUID.randomUUID(), ext);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(file.getSize());
        try (InputStream in = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(bucket(), storageKey, in, metadata);
            cosClient.putObject(request);
        } catch (IOException | CosClientException e) {
            throw new AttachmentException(Msg.t("error.attachment.storeFailed"), "STORAGE_ERROR");
        }
        return storageKey;
    }

    @Override
    public String resolveUrl(String storageKey) {
        return publicBaseUrl() + storageKey;
    }

    @Override
    public Optional<String> extractStorageKey(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String prefix = publicBaseUrl();
        if (!url.startsWith(prefix)) {
            return Optional.empty();
        }
        String key = url.substring(prefix.length());
        if (key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    @Override
    public byte[] read(String storageKey) {
        if (storageKey == null || storageKey.isBlank()
                || storageKey.contains("..")
                || storageKey.startsWith("/")) {
            throw new AttachmentException(Msg.t("error.attachment.readFailed"), "STORAGE_ERROR");
        }
        try {
            COSObject object = cosClient.getObject(bucket(), storageKey);
            try (InputStream in = object.getObjectContent()) {
                return in.readAllBytes();
            }
        } catch (IOException | CosClientException e) {
            throw new AttachmentException(Msg.t("error.attachment.readFailed"), "STORAGE_ERROR");
        }
    }

    static String portalFor(String ownerType) {
        if (TEACHER_OWNER_TYPES.contains(ownerType)) {
            return "teacher";
        }
        if ("submission".equals(ownerType)) {
            return "students";
        }
        throw new AttachmentException(Msg.t("error.attachment.storeFailed"), "STORAGE_ERROR");
    }

    private String bucket() {
        return props.getTencentCos().getBucket();
    }

    private String publicBaseUrl() {
        StorageProperties.TencentCos cos = props.getTencentCos();
        return "https://" + cos.getBucket() + ".cos." + cos.getRegion() + ".myqcloud.com/";
    }
}
