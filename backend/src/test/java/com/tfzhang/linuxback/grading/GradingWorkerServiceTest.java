package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingWorkerServiceTest {

    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private ExperimentQuestionMapper experimentQuestionMapper;
    @Mock
    private SubQuestionMapper subQuestionMapper;
    @Mock
    private SubQuestionGrader subQuestionGrader;
    @Mock
    private GradingPersistenceService gradingPersistenceService;

    private GradingWorkerService worker;

    @BeforeEach
    void setUp() {
        worker = new GradingWorkerService(
                submissionMapper,
                experimentQuestionMapper,
                subQuestionMapper,
                subQuestionGrader,
                gradingPersistenceService,
                new ObjectMapper());
    }

    @Test
    void handle_duplicateWhenAlreadyClaimed() {
        Submission row = pending(900L);
        row.setStatus(1);
        when(submissionMapper.selectById(900L)).thenReturn(row);

        assertEquals(GradingOutcome.DUPLICATE, worker.handle(900L));
        verify(submissionMapper, never()).claimPending(anyLong(), anyInt());
        verify(gradingPersistenceService, never()).saveCompleted(any(), any(), anyInt());
    }

    @Test
    void handle_duplicateWhenCasLoses() {
        when(submissionMapper.selectById(900L)).thenReturn(pending(900L));
        when(submissionMapper.claimPending(900L, 0)).thenReturn(0);

        assertEquals(GradingOutcome.DUPLICATE, worker.handle(900L));
        verify(gradingPersistenceService, never()).saveCompleted(any(), any(), anyInt());
    }

    @Test
    void handle_missingSubmission_isPermanent() {
        when(submissionMapper.selectById(900L)).thenReturn(null);

        assertThrows(PermanentGradingException.class, () -> worker.handle(900L));
        verify(gradingPersistenceService, never()).markFailed(anyLong());
    }

    @Test
    void handle_scoresAllSubQuestionsThenPersists() {
        when(submissionMapper.selectById(900L)).thenReturn(pending(900L));
        when(submissionMapper.claimPending(900L, 0)).thenReturn(1);
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setQuestionId(12L);
        when(experimentQuestionMapper.selectById(41L)).thenReturn(eq);
        SubQuestion sq = new SubQuestion();
        sq.setId(101L);
        sq.setQuestionId(12L);
        sq.setOrderIndex(0);
        sq.setAnswerFormat("text");
        sq.setScore(5);
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(sq));
        when(subQuestionGrader.grade(eq(sq), any()))
                .thenReturn(new SubQuestionGrader.GradeResult(5, "ok", 0, "log"));

        assertEquals(GradingOutcome.COMPLETED, worker.handle(900L));

        ArgumentCaptor<List<SubQuestionScore>> scores = ArgumentCaptor.forClass(List.class);
        verify(gradingPersistenceService).saveCompleted(any(Submission.class), scores.capture(), eq(5));
        assertEquals(1, scores.getValue().size());
        assertEquals(101L, scores.getValue().get(0).getSubQuestionId());
        assertEquals(5, scores.getValue().get(0).getAiScore().intValue());
        assertEquals(5, scores.getValue().get(0).getEffectiveScore().intValue());
    }

    @Test
    void handle_invalidAnswerBlocks_marksFailed() {
        Submission row = pending(900L);
        row.setAnswerBlocks("{");
        when(submissionMapper.selectById(900L)).thenReturn(row);
        when(submissionMapper.claimPending(900L, 0)).thenReturn(1);
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setQuestionId(12L);
        when(experimentQuestionMapper.selectById(41L)).thenReturn(eq);
        when(subQuestionMapper.selectList(any())).thenReturn(List.of());

        assertEquals(GradingOutcome.PERMANENT_FAILURE, worker.handle(900L));
        verify(gradingPersistenceService).markFailed(900L);
        verify(gradingPersistenceService, never()).saveCompleted(any(), any(), anyInt());
    }

    @Test
    void handle_emptyAnswersStillGrade() {
        Submission row = pending(900L);
        row.setAnswerBlocks(JsonNodeFactory.instance.arrayNode().toString());
        when(submissionMapper.selectById(900L)).thenReturn(row);
        when(submissionMapper.claimPending(900L, 0)).thenReturn(1);
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setQuestionId(12L);
        when(experimentQuestionMapper.selectById(41L)).thenReturn(eq);
        SubQuestion sq = new SubQuestion();
        sq.setId(101L);
        sq.setScore(8);
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(sq));
        when(subQuestionGrader.grade(any(), any()))
                .thenReturn(new SubQuestionGrader.GradeResult(8, "p", 0, "p"));

        assertEquals(GradingOutcome.COMPLETED, worker.handle(900L));
        verify(gradingPersistenceService).saveCompleted(any(), any(), eq(8));
    }

    private static Submission pending(long id) {
        Submission row = new Submission();
        row.setId(id);
        row.setExperimentQuestionId(41L);
        row.setStatus(0);
        row.setVersion(0);
        row.setIsManualOverride(0);
        row.setAnswerBlocks("[]");
        return row;
    }
}
