package com.tfzhang.linuxback.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface AttachmentStorageService {

    /**
     * Persists the file and returns the storageKey to save on the attachment row.
     */
    String upload(MultipartFile file, String ownerType);

    /** Resolves a stored storageKey into a URL the frontend can load. */
    String resolveUrl(String storageKey);

    /** Extracts the storageKey from a URL this backend produced, or empty if the URL is not ours. */
    Optional<String> extractStorageKey(String url);

    /** Reads stored bytes for OCR; must reject path traversal. */
    byte[] read(String storageKey);
}
