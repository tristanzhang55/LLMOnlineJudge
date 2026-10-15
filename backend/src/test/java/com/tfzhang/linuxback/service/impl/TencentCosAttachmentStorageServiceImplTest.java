package com.tfzhang.linuxback.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.tfzhang.linuxback.common.AttachmentException;
import com.tfzhang.linuxback.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TencentCosAttachmentStorageServiceImplTest {

    @Mock
    private COSClient cosClient;

    private TencentCosAttachmentStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.getTencentCos().setBucket("linuxjudge-1301303075");
        props.getTencentCos().setRegion("ap-shanghai");
        storage = new TencentCosAttachmentStorageServiceImpl(props, cosClient);
    }

    @Test
    void upload_question_usesTeacherPrefix() {
        MockMultipartFile file = png();
        String key = storage.upload(file, "question");
        assertTrue(key.matches("^teacher/question/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png$"));
        assertPutKey(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"question_answer", "sub_question", "sub_question_answer"})
    void upload_teacherOwnerTypes_startWithTeacher(String ownerType) {
        String key = storage.upload(png(), ownerType);
        assertTrue(key.startsWith("teacher/" + ownerType + "/"));
        assertPutKey(key);
    }

    @Test
    void upload_submission_usesStudentsPrefix() {
        String key = storage.upload(png(), "submission");
        assertTrue(key.matches("^students/submission/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png$"));
        assertPutKey(key);
    }

    @Test
    void resolveUrl_isPublicCosHost() {
        String key = "teacher/question/2026/09/x.png";
        assertEquals(
                "https://linuxjudge-1301303075.cos.ap-shanghai.myqcloud.com/" + key,
                storage.resolveUrl(key));
    }

    @Test
    void extractStorageKey_reversesResolveUrl() {
        String key = "students/submission/2026/09/x.png";
        assertEquals(key, storage.extractStorageKey(storage.resolveUrl(key)).orElseThrow());
    }

    @Test
    void extractStorageKey_emptyForLocalAndForeignUrls() {
        assertFalse(storage.extractStorageKey("/api/files/question/2026/09/x.png").isPresent());
        assertFalse(storage.extractStorageKey("https://example.com/teacher/question/x.png").isPresent());
    }

    @Test
    void read_rejectsTraversalBeforeCosCall() {
        AttachmentException ex = assertThrows(
                AttachmentException.class, () -> storage.read("teacher/../secret.png"));
        assertEquals("STORAGE_ERROR", ex.getCode());
        assertEquals("Failed to read file", ex.getMessage());
        verify(cosClient, never()).getObject(any(String.class), any(String.class));
    }

    @Test
    void read_rejectsLeadingSlashBeforeCosCall() {
        assertThrows(AttachmentException.class, () -> storage.read("/teacher/question/x.png"));
        verify(cosClient, never()).getObject(any(String.class), any(String.class));
    }

    @Test
    void propertyGates_doNotOverlap() {
        ConditionalOnProperty local = annotation(LocalAttachmentStorageServiceImpl.class);
        ConditionalOnProperty cos = annotation(TencentCosAttachmentStorageServiceImpl.class);
        assertTrue(Arrays.asList(local.havingValue()).contains("local")
                || "local".equals(local.havingValue()));
        assertTrue(local.matchIfMissing());
        assertEquals("tencent-cos", cos.havingValue());
        assertFalse(cos.matchIfMissing());
        assertEquals("app.storage.type", local.name()[0]);
        assertEquals("app.storage.type", cos.name()[0]);
    }

    @Test
    void yamlSecrets_areEmptyPlaceholders() throws Exception {
        Path yml = Path.of("src/main/resources/application.yml");
        String text = Files.readString(yml, StandardCharsets.UTF_8);
        assertTrue(text.contains("secret-id: ${COS_SECRET_ID:}"));
        assertTrue(text.contains("secret-key: ${COS_SECRET_KEY:}"));
        assertFalse(text.contains("AKID"));
    }

    private void assertPutKey(String key) {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(captor.capture());
        assertEquals(key, captor.getValue().getKey());
        assertEquals("linuxjudge-1301303075", captor.getValue().getBucketName());
        assertEquals("image/png", captor.getValue().getMetadata().getContentType());
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "sample.png", "image/png", new byte[]{1, 2, 3, 4});
    }

    private static ConditionalOnProperty annotation(Class<?> type) {
        Annotation raw = type.getAnnotation(ConditionalOnProperty.class);
        return (ConditionalOnProperty) raw;
    }
}
