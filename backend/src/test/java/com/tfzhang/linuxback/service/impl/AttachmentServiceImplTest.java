package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.AttachmentException;
import com.tfzhang.linuxback.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.entity.Attachment;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.AttachmentMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.AttachmentStorageService;
import com.tfzhang.linuxback.service.MarkdownOwner;
import com.tfzhang.linuxback.vo.AttachmentVO;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    private static final long TEACHER_ID = 1L;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private AttachmentStorageService storageService;

    @Mock
    private SubmissionMapper submissionMapper;

    @InjectMocks
    private AttachmentServiceImpl attachmentService;

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_ID);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void upload_oversizedFile_throwsWithoutCallingStorage() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", content);

        AttachmentException ex = assertThrows(
                AttachmentException.class, () -> attachmentService.upload(file, "question"));

        assertEquals("FILE_TOO_LARGE", ex.getCode());
        verify(storageService, never()).upload(any(), any());
        verify(attachmentMapper, never()).insert(any(Attachment.class));
    }

    @Test
    void upload_unsupportedType_throwsWithoutCallingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        AttachmentException ex = assertThrows(
                AttachmentException.class, () -> attachmentService.upload(file, "question"));

        assertEquals("UNSUPPORTED_FILE_TYPE", ex.getCode());
        verify(storageService, never()).upload(any(), any());
        verify(attachmentMapper, never()).insert(any(Attachment.class));
    }

    @Test
    void upload_unknownOwnerType_throwsWithoutCallingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{1, 2, 3});

        AttachmentException ex = assertThrows(
                AttachmentException.class, () -> attachmentService.upload(file, "questions"));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        verify(storageService, never()).upload(any(), any());
        verify(attachmentMapper, never()).insert(any(Attachment.class));
    }

    @Test
    void upload_success_insertsUnattachedRow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{1, 2, 3});
        String storageKey = "question/2026/08/test-uuid.png";

        when(storageService.upload(file, "question")).thenReturn(storageKey);
        when(storageService.resolveUrl(storageKey)).thenReturn("/api/files/" + storageKey);
        when(attachmentMapper.insert(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment row = invocation.getArgument(0);
            row.setId(42L);
            return 1;
        });

        AttachmentVO result = attachmentService.upload(file, "question");

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(captor.capture());

        Attachment saved = captor.getValue();
        assertEquals(0L, saved.getOwnerId());
        assertEquals(TEACHER_ID, saved.getUploaderId());
        assertEquals(storageKey, saved.getStorageKey());
        assertEquals("image/png", saved.getMimeType());
        assertEquals(3L, saved.getFileSize());

        assertEquals(42L, result.getId());
        assertEquals(storageKey, result.getStorageKey());
        assertEquals("/api/files/" + storageKey, result.getUrl());
    }

    @Test
    void attachMarkdownImages_skipsUpdateWhenNoKeysFound() {
        attachmentService.attachMarkdownImages("question", 5L, "plain text");

        verify(attachmentMapper, never()).update(any(), any());
    }

    @Test
    void attachMarkdownImages_updatesUnattachedRowsForExtractedKeys() {
        String url = "/api/files/question/2026/08/x.png";
        String markdown = "![](%s)".formatted(url);
        when(storageService.extractStorageKey(url)).thenReturn(Optional.of("question/2026/08/x.png"));

        attachmentService.attachMarkdownImages("question", 5L, markdown);

        verify(attachmentMapper).update(any(), any());
        verify(storageService).extractStorageKey(url);
    }

    @Test
    void reconcileMarkdownImages_claimsUnattachedMatchingUpload() {
        String key = "question/2026/08/new.png";
        String url = "/api/files/" + key;
        Attachment unattached = attachmentRow(1L, "question", 0L, key);

        when(storageService.extractStorageKey(url)).thenReturn(Optional.of(key));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(unattached))
                .thenReturn(List.of());

        attachmentService.reconcileMarkdownImages(
                List.of(new MarkdownOwner("question", 29L)),
                List.of(new MarkdownOwner("question", 29L, "![](%s)".formatted(url))));

        verify(attachmentMapper).update(any(), any());
    }

    @Test
    void reconcileMarkdownImages_softDeletesRemovedAggregateOwnedRows() {
        String retainedKey = "question/2026/08/keep.png";
        String removedKey = "sub_question/2026/08/remove.png";
        Attachment removed = attachmentRow(2L, "sub_question", 2901L, removedKey);

        when(storageService.extractStorageKey("/api/files/" + retainedKey))
                .thenReturn(Optional.of(retainedKey));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(removed))
                .thenReturn(List.of(removed));

        attachmentService.reconcileMarkdownImages(
                List.of(
                        new MarkdownOwner("question", 29L),
                        new MarkdownOwner("sub_question", 2901L)),
                List.of(new MarkdownOwner(
                        "question",
                        29L,
                        "![](/api/files/" + retainedKey + ")")));

        verify(attachmentMapper).deleteById(2L);
    }

    @Test
    void reconcileMarkdownImages_ignoresForeignAndMockUrls() {
        when(storageService.extractStorageKey("https://example.com/x.png")).thenReturn(Optional.empty());
        when(storageService.extractStorageKey("/mock/attachments/sample-1.png")).thenReturn(Optional.empty());
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        attachmentService.reconcileMarkdownImages(
                List.of(new MarkdownOwner("question", 29L)),
                List.of(new MarkdownOwner(
                        "question",
                        29L,
                        "![](https://example.com/x.png) ![]( /mock/attachments/sample-1.png )")));

        verify(attachmentMapper, never()).update(any(), any());
        verify(attachmentMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void softDeleteAggregateAttachments_deletesQuestionAndSubQuestionRows() {
        Attachment questionOwned = attachmentRow(1L, "question", 29L, "question/2026/08/a.png");
        Attachment subOwned = attachmentRow(2L, "sub_question", 2901L, "sub_question/2026/08/b.png");
        Attachment answerOwned = attachmentRow(3L, "sub_question_answer", 2901L, "sub_question_answer/2026/08/c.png");

        when(attachmentMapper.selectList(any())).thenReturn(List.of(questionOwned, subOwned, answerOwned));

        attachmentService.softDeleteAggregateAttachments(29L, List.of(2901L));

        verify(attachmentMapper).deleteById(1L);
        verify(attachmentMapper).deleteById(2L);
        verify(attachmentMapper).deleteById(3L);
    }

    @Test
    void softDeleteAggregateAttachments_emptySubQuestionIds_stillDeletesQuestionOwned() {
        Attachment questionOwned = attachmentRow(1L, "question", 29L, "question/2026/08/a.png");

        when(attachmentMapper.selectList(any())).thenReturn(List.of(questionOwned));

        attachmentService.softDeleteAggregateAttachments(29L, List.of());

        verify(attachmentMapper).deleteById(1L);
        verify(attachmentMapper, times(1)).deleteById(any(Long.class));
    }

    @Test
    void softDeleteAggregateAttachments_noMatchingRows_isNoOp() {
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        attachmentService.softDeleteAggregateAttachments(29L, List.of(2901L));

        verify(attachmentMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void upload_studentQuestionOwnerType_isForbidden() {
        TestAuth.setStudent(2L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{1, 2, 3});

        AuthException ex = assertThrows(
                AuthException.class, () -> attachmentService.upload(file, "question"));

        assertEquals("FORBIDDEN", ex.getCode());
        verify(storageService, never()).upload(any(), any());
    }

    @Test
    void upload_studentForeignSubmission_isNotFound() {
        TestAuth.setStudent(2L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{1, 2, 3});
        Submission other = new Submission();
        other.setId(900L);
        other.setStudentId(99L);
        when(submissionMapper.selectById(900L)).thenReturn(other);

        ExperimentException ex = assertThrows(
                ExperimentException.class,
                () -> attachmentService.upload(file, "submission", 900L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(storageService, never()).upload(any(), any());
    }

    @Test
    void upload_studentOwnDraft_setsOwnerId() {
        TestAuth.setStudent(2L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{1, 2, 3});
        Submission draft = new Submission();
        draft.setId(900L);
        draft.setStudentId(2L);
        when(submissionMapper.selectById(900L)).thenReturn(draft);
        when(storageService.upload(file, "submission")).thenReturn("submission/2026/08/x.png");
        when(storageService.resolveUrl("submission/2026/08/x.png")).thenReturn("/api/files/submission/2026/08/x.png");
        when(attachmentMapper.insert(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment row = invocation.getArgument(0);
            row.setId(7L);
            return 1;
        });

        AttachmentVO result = attachmentService.upload(file, "submission", 900L);

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertEquals(900L, captor.getValue().getOwnerId());
        assertEquals("submission", captor.getValue().getOwnerType());
        assertEquals(2L, captor.getValue().getUploaderId());
        assertEquals(7L, result.getId());
    }

    private static Attachment attachmentRow(long id, String ownerType, long ownerId, String storageKey) {
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setOwnerType(ownerType);
        attachment.setOwnerId(ownerId);
        attachment.setStorageKey(storageKey);
        attachment.setMimeType("image/png");
        attachment.setFileSize(10L);
        attachment.setUploaderId(TEACHER_ID);
        attachment.setOrderIndex(0);
        attachment.setIsDelete(0);
        return attachment;
    }
}
