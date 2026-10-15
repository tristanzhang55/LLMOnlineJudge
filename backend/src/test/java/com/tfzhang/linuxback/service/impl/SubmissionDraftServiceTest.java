package com.tfzhang.linuxback.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.SubmissionQuery;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.SubmissionDraftVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionDraftServiceTest {

    private static final long STUDENT_ID = 2L;

    @Mock
    private ExperimentQuestionMapper experimentQuestionMapper;
    @Mock
    private ExperimentMapper experimentMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private SubmissionQuery submissionQuery;
    @Mock
    private ExperimentService experimentService;

    private SubmissionDraftServiceImpl draftService;

    @BeforeEach
    void setUp() {
        TestAuth.setStudent(STUDENT_ID);
        draftService = new SubmissionDraftServiceImpl(
                experimentQuestionMapper,
                experimentMapper,
                classStudentMapper,
                submissionMapper,
                submissionQuery,
                experimentService,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void saveDraft_secondPutKeepsSameIdAndLeavesSubmitTimeNull() {
        stubPublishedQuestion();
        when(submissionQuery.isLocked(5L, STUDENT_ID)).thenReturn(false);
        when(submissionMapper.upsertDraft(any(Submission.class))).thenReturn(1);
        Submission stored = new Submission();
        stored.setId(77L);
        stored.setStatus(-1);
        stored.setSubmitTime(null);
        when(submissionMapper.findDraft(41L, STUDENT_ID)).thenReturn(stored);

        SubmissionDraftVO first = draftService.saveDraft(41L, JsonNodeFactory.instance.arrayNode());
        SubmissionDraftVO second = draftService.saveDraft(41L, JsonNodeFactory.instance.arrayNode());

        assertEquals(77L, first.getId());
        assertEquals(77L, second.getId());
        assertEquals(-1, stored.getStatus());
        assertNull(stored.getSubmitTime());
        verify(submissionMapper, times(2)).upsertDraft(any(Submission.class));
        verify(submissionMapper, never()).insert(any(Submission.class));
    }

    @Test
    void persistDraftRow_concurrentCallsShareOneDraftId() throws Exception {
        when(submissionMapper.upsertDraft(any(Submission.class))).thenReturn(1);
        Submission stored = new Submission();
        stored.setId(77L);
        when(submissionMapper.findDraft(41L, STUDENT_ID)).thenReturn(stored);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger failures = new AtomicInteger();
        Runnable race = () -> {
            try {
                start.await();
                assertEquals(77L, draftService.persistDraftRow(41L, STUDENT_ID, "[]"));
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
        Thread t1 = new Thread(race);
        Thread t2 = new Thread(race);
        t1.start();
        t2.start();
        start.countDown();
        done.await();

        assertEquals(0, failures.get());
        verify(submissionMapper, times(2)).upsertDraft(any(Submission.class));
        verify(submissionMapper, never()).insert(any(Submission.class));
    }

    @Test
    void saveDraft_whenLocked_isForbidden() {
        stubPublishedQuestion();
        when(submissionQuery.isLocked(5L, STUDENT_ID)).thenReturn(true);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                draftService.saveDraft(41L, JsonNodeFactory.instance.arrayNode()));

        assertEquals("FORBIDDEN", ex.getCode());
        verify(submissionMapper, never()).upsertDraft(any());
    }

    @Test
    void saveDraft_whenClosed_isConflict() {
        stubPublishedQuestion();
        Experiment closed = publishedExperiment();
        closed.setStatus(2);
        when(experimentMapper.selectById(5L)).thenReturn(closed);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                draftService.saveDraft(41L, JsonNodeFactory.instance.arrayNode()));

        assertEquals("CONFLICT", ex.getCode());
        verify(submissionMapper, never()).upsertDraft(any());
    }

    @Test
    void saveDraft_whenAssignmentDraft_isNotFound() {
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setExperimentId(5L);
        when(experimentQuestionMapper.selectById(41L)).thenReturn(eq);
        Experiment draft = publishedExperiment();
        draft.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(draft);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                draftService.saveDraft(41L, JsonNodeFactory.instance.arrayNode()));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(submissionMapper, never()).upsertDraft(any());
    }

    private void stubPublishedQuestion() {
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setExperimentId(5L);
        when(experimentQuestionMapper.selectById(41L)).thenReturn(eq);
        when(experimentMapper.selectById(5L)).thenReturn(publishedExperiment());
        when(classStudentMapper.selectCount(any())).thenReturn(1L);
    }

    private static Experiment publishedExperiment() {
        Experiment experiment = new Experiment();
        experiment.setId(5L);
        experiment.setClassId(1L);
        experiment.setStatus(1);
        experiment.setLastPublishedTime(LocalDateTime.of(2026, 8, 20, 10, 0));
        return experiment;
    }
}
