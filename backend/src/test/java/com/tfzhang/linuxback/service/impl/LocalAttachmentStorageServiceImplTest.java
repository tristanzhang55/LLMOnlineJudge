package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalAttachmentStorageServiceImplTest {

    @TempDir
    Path tempDir;

    private LocalAttachmentStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getLocal().setBasePath(tempDir.toString());
        props.getLocal().setUrlPrefix("/files");

        storageService = new LocalAttachmentStorageServiceImpl(props);
        ReflectionTestUtils.setField(storageService, "contextPath", "/api");
        storageService.init();
    }

    @Test
    void upload_writesFileAndReturnsExpectedKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{1, 2, 3, 4});

        String storageKey = storageService.upload(file, "question");

        assertTrue(storageKey.matches("^question/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png$"));
        assertTrue(Files.exists(tempDir.resolve(storageKey)));
        assertEquals(4, Files.size(tempDir.resolve(storageKey)));
    }

    @Test
    void resolveUrl_includesContextPathAndUrlPrefix() {
        String url = storageService.resolveUrl("question/2026/08/x.png");

        assertTrue(url.startsWith("/api/files/"));
        assertEquals("/api/files/question/2026/08/x.png", url);
    }

    @Test
    void extractStorageKey_reversesResolveUrl() {
        String key = "question/2026/08/x.png";
        assertEquals(key, storageService.extractStorageKey(storageService.resolveUrl(key)).orElseThrow());
    }

    @Test
    void extractStorageKey_returnsEmptyForForeignUrls() {
        assertFalse(storageService.extractStorageKey("https://example.com/x.png").isPresent());
        assertFalse(storageService.extractStorageKey("/mock/attachments/sample-1.png").isPresent());
    }

    @Test
    void extractStorageKey_parsesDirectApiFilesUrl() {
        assertEquals(
                "question/2026/08/x.png",
                storageService.extractStorageKey("/api/files/question/2026/08/x.png").orElseThrow());
    }

    @Test
    void read_returnsUploadedBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{9, 8, 7});
        String storageKey = storageService.upload(file, "submission");

        byte[] bytes = storageService.read(storageKey);

        assertEquals(3, bytes.length);
        assertEquals(9, bytes[0]);
    }
}
