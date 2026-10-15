package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.dto.DisputeForwardRequest;
import com.tfzhang.linuxback.dto.DisputeResolveRequest;
import com.tfzhang.linuxback.dto.DisputeResolveScoreItem;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mq.GradingQueuePublisher;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.TeacherDisputeForwardItemVO;
import com.tfzhang.linuxback.vo.TeacherDisputeForwardResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherDisputeServiceImplTest {

    private static final long TEACHER_ID = 9L;
    private static final long EXPERIMENT_ID = 18L;
    private static final long QUESTION_ID = 40L;

    @Mock
    private DisputeMapper disputeMapper;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private ExperimentQuestionMapper experimentQuestionMapper;
    @Mock
    private ExperimentService experimentService;
    @Mock
    private SubQuestionMapper subQuestionMapper;
    @Mock
    private SubQuestionScoreMapper subQuestionScoreMapper;
    @Mock
    private GradingQueuePublisher gradingQueuePublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TeacherDisputeServiceImpl service;

    @BeforeAll
    static void initMp() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_ID);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());
        service = new TeacherDisputeServiceImpl(
                disputeMapper,
                submissionMapper,
                experimentQuestionMapper,
                experimentService,
                subQuestionMapper,
                subQuestionScoreMapper,
                gradingQueuePublisher,
                transactionManager);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void resolve_isTransactional() throws NoSuchMethodException {
        assertTrue(TeacherDisputeServiceImpl.class
                .getDeclaredMethod("resolve", long.class, DisputeResolveRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void resolve_commentOnlyStatus2_doesNotTouchScores() {
        stubOwnedOpen(501L, submission(900L, 2, 10, 1));
        when(disputeMapper.casResolve(eq(501L), eq("维持原判"), any())).thenReturn(1);
        when(subQuestionScoreMapper.sumEffectiveScore(900L)).thenReturn(BigDecimal.TEN);
        when(subQuestionScoreMapper.existsManualScore(900L)).thenReturn(true);

        DisputeVO vo = service.resolve(501L, commentOnly("维持原判"));

        assertEquals(2, vo.getDisputeStatus());
        assertEquals("维持原判", vo.getManualComment());
        verify(subQuestionScoreMapper, never()).upsertManualScore(anyLong(), anyLong(), any(), any());
        verify(submissionMapper, never()).resetForRegrade(anyLong());
        verify(gradingQueuePublisher, never()).publishSubmission(anyLong());
    }

    @Test
    void resolve_scoresOnStatus2_upsertsManualOnly() {
        stubOwnedOpen(501L, submission(900L, 2, 4, 0));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bank(101L, 5)));
        when(disputeMapper.casResolve(eq(501L), eq("改分"), any())).thenReturn(1);
        when(subQuestionScoreMapper.sumEffectiveScore(900L)).thenReturn(BigDecimal.valueOf(5));
        when(subQuestionScoreMapper.existsManualScore(900L)).thenReturn(true);

        service.resolve(501L, withScore("改分", 101L, 5));

        verify(subQuestionScoreMapper).upsertManualScore(
                900L, 101L, BigDecimal.valueOf(5), BigDecimal.valueOf(5));
        verify(subQuestionScoreMapper, never()).upsertAiGrade(any());
    }

    @Test
    void resolve_scoresOnStatus3_insertsAndMovesStatusTo2() {
        stubOwnedOpen(501L, submission(900L, 3, null, 0));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bank(101L, 5)));
        when(disputeMapper.casResolve(eq(501L), eq("补分"), any())).thenReturn(1);
        when(subQuestionScoreMapper.sumEffectiveScore(900L)).thenReturn(BigDecimal.valueOf(5));
        when(subQuestionScoreMapper.existsManualScore(900L)).thenReturn(true);
        when(subQuestionScoreMapper.countBySubmissionId(900L)).thenReturn(1L);

        service.resolve(501L, withScore("补分", 101L, 5));

        verify(subQuestionScoreMapper).upsertManualScore(
                900L, 101L, BigDecimal.valueOf(5), BigDecimal.valueOf(5));
        verify(submissionMapper, times(3)).update(eq(null), any());
    }

    @Test
    void resolve_commentOnlyStatus3_doesNotInsertAndLeavesStatus() {
        stubOwnedOpen(501L, submission(900L, 3, null, 0));
        when(disputeMapper.casResolve(eq(501L), eq("无法评分"), any())).thenReturn(1);
        when(subQuestionScoreMapper.sumEffectiveScore(900L)).thenReturn(BigDecimal.ZERO);
        when(subQuestionScoreMapper.existsManualScore(900L)).thenReturn(false);
        when(subQuestionScoreMapper.countBySubmissionId(900L)).thenReturn(0L);

        service.resolve(501L, commentOnly("无法评分"));

        verify(subQuestionScoreMapper, never()).upsertManualScore(anyLong(), anyLong(), any(), any());
        verify(subQuestionScoreMapper).countBySubmissionId(900L);
    }

    @Test
    void resolve_laterSubQuestionOnAlreadyStatus2_stillUpserts() {
        stubOwnedOpen(501L, submission(900L, 2, 5, 1));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bank(101L, 5), bank(102L, 5)));
        when(disputeMapper.casResolve(eq(501L), eq("补B"), any())).thenReturn(1);
        when(subQuestionScoreMapper.sumEffectiveScore(900L)).thenReturn(BigDecimal.TEN);
        when(subQuestionScoreMapper.existsManualScore(900L)).thenReturn(true);

        service.resolve(501L, withScore("补B", 102L, 5));

        verify(subQuestionScoreMapper).upsertManualScore(
                900L, 102L, BigDecimal.valueOf(5), BigDecimal.valueOf(5));
    }

    @Test
    void resolve_alreadyHandled_isConflict() {
        Dispute open = ownedDispute(501L, 900L);
        Dispute handled = ownedDispute(501L, 900L);
        handled.setResolveTime(LocalDateTime.of(2026, 9, 10, 12, 0));
        when(disputeMapper.selectById(501L)).thenReturn(open, handled);
        when(submissionMapper.selectById(900L)).thenReturn(submission(900L, 2, 10, 0));
        when(experimentQuestionMapper.selectById(70L)).thenReturn(experimentQuestion());
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(experiment());
        when(disputeMapper.casResolve(eq(501L), eq("晚了"), any())).thenReturn(0);

        ExperimentException ex = assertThrows(
                ExperimentException.class, () -> service.resolve(501L, commentOnly("晚了")));

        assertEquals("CONFLICT", ex.getCode());
        assertEquals("该申诉已被处理", ex.getMessage());
        verify(subQuestionScoreMapper, never()).upsertManualScore(anyLong(), anyLong(), any(), any());
    }

    @Test
    void resolve_alreadyWithdrawn_isConflict() {
        Dispute open = ownedDispute(501L, 900L);
        Dispute withdrawn = ownedDispute(501L, 900L);
        withdrawn.setWithdrawnTime(LocalDateTime.of(2026, 9, 10, 9, 0));
        when(disputeMapper.selectById(501L)).thenReturn(open, withdrawn);
        when(submissionMapper.selectById(900L)).thenReturn(submission(900L, 2, 10, 0));
        when(experimentQuestionMapper.selectById(70L)).thenReturn(experimentQuestion());
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(experiment());
        when(disputeMapper.casResolve(eq(501L), eq("晚了"), any())).thenReturn(0);

        ExperimentException ex = assertThrows(
                ExperimentException.class, () -> service.resolve(501L, commentOnly("晚了")));

        assertEquals("CONFLICT", ex.getCode());
        assertEquals("该申诉已被学生撤回", ex.getMessage());
    }

    @Test
    void resolve_foreignSubQuestion_isInvalid() {
        stubOwnedOpen(501L, submission(900L, 2, 10, 0));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bank(101L, 5)));

        ExperimentException ex = assertThrows(
                ExperimentException.class, () -> service.resolve(501L, withScore("改", 999L, 1)));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("subQuestionId", ex.getFieldErrors().get(0).field());
        verify(disputeMapper, never()).casResolve(anyLong(), any(), any());
    }

    @Test
    void resolve_scoreOutOfRange_isInvalid() {
        stubOwnedOpen(501L, submission(900L, 2, 10, 0));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bank(101L, 5)));

        ExperimentException ex = assertThrows(
                ExperimentException.class, () -> service.resolve(501L, withScore("改", 101L, 9)));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("manualScore", ex.getFieldErrors().get(0).field());
        verify(disputeMapper, never()).casResolve(anyLong(), any(), any());
    }

    @Test
    void resolve_missing_isNotFound() {
        when(disputeMapper.selectById(501L)).thenReturn(null);

        ExperimentException ex = assertThrows(
                ExperimentException.class, () -> service.resolve(501L, commentOnly("x")));

        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void forward_mixedBatch_publishesOnlyForwarded() {
        stubOwnedOpen(501L, submission(900L, 2, 10, 1));
        stubOwnedOpen(507L, submission(901L, 3, null, 0));
        Dispute resolved = ownedDispute(503L, 902L);
        resolved.setResolveTime(LocalDateTime.of(2026, 9, 10, 12, 0));
        when(disputeMapper.selectById(503L)).thenReturn(resolved);
        when(submissionMapper.selectById(902L)).thenReturn(submission(902L, 2, 8, 0));
        when(experimentQuestionMapper.selectById(70L)).thenReturn(experimentQuestion());
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(experiment());
        when(disputeMapper.casResolve(eq(501L), eq("rubric"), any())).thenReturn(1);
        when(disputeMapper.casResolve(eq(503L), eq("rubric"), any())).thenReturn(0);
        when(disputeMapper.casResolve(eq(507L), eq("rubric"), any())).thenReturn(1);
        when(submissionMapper.resetForRegrade(900L)).thenReturn(1);
        when(submissionMapper.resetForRegrade(901L)).thenReturn(1);

        TeacherDisputeForwardResultVO vo = service.forward(forwardRequest(501L, 503L, 507L));

        assertEquals(List.of("forwarded", "already_resolved", "forwarded"),
                vo.getResults().stream().map(TeacherDisputeForwardItemVO::getStatus).toList());
        verify(gradingQueuePublisher).publishSubmission(900L);
        verify(gradingQueuePublisher).publishSubmission(901L);
        verify(gradingQueuePublisher, never()).publishSubmission(902L);
        verify(subQuestionScoreMapper).clearManualScores(900L);
        verify(subQuestionScoreMapper).clearManualScores(901L);
    }

    @Test
    void forward_conflict_doesNotPublish() {
        stubOwnedOpen(501L, submission(900L, 2, 10, 1));
        when(disputeMapper.casResolve(eq(501L), eq("rubric"), any())).thenReturn(1);
        when(submissionMapper.resetForRegrade(900L)).thenReturn(0);

        TeacherDisputeForwardResultVO vo = service.forward(forwardRequest(501L));

        assertEquals("conflict", vo.getResults().get(0).getStatus());
        verify(subQuestionScoreMapper).clearManualScores(900L);
        verify(gradingQueuePublisher, never()).publishSubmission(anyLong());
    }

    @Test
    void forward_notFoundInBatch_stillProcessesOthers() {
        when(disputeMapper.selectById(599L)).thenReturn(null);
        stubOwnedOpen(501L, submission(900L, 2, 10, 0));
        when(disputeMapper.casResolve(eq(501L), eq("rubric"), any())).thenReturn(1);
        when(submissionMapper.resetForRegrade(900L)).thenReturn(1);

        TeacherDisputeForwardResultVO vo = service.forward(forwardRequest(599L, 501L));

        assertEquals("not_found", vo.getResults().get(0).getStatus());
        assertEquals("forwarded", vo.getResults().get(1).getStatus());
        verify(gradingQueuePublisher).publishSubmission(900L);
    }

    @Test
    void forward_unauthorized_isNotFound() {
        Dispute dispute = ownedDispute(501L, 900L);
        when(disputeMapper.selectById(501L)).thenReturn(dispute);
        when(submissionMapper.selectById(900L)).thenReturn(submission(900L, 2, 10, 0));
        when(experimentQuestionMapper.selectById(70L)).thenReturn(experimentQuestion());
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID))
                .thenThrow(ExperimentException.notFound());

        TeacherDisputeForwardResultVO vo = service.forward(forwardRequest(501L));

        assertEquals("not_found", vo.getResults().get(0).getStatus());
        verify(disputeMapper, never()).casResolve(anyLong(), any(), any());
        verify(gradingQueuePublisher, never()).publishSubmission(anyLong());
    }

    @Test
    void forward_alreadyWithdrawn() {
        Dispute open = ownedDispute(501L, 900L);
        Dispute withdrawn = ownedDispute(501L, 900L);
        withdrawn.setWithdrawnTime(LocalDateTime.of(2026, 9, 9, 8, 0));
        when(disputeMapper.selectById(501L)).thenReturn(open, withdrawn);
        when(submissionMapper.selectById(900L)).thenReturn(submission(900L, 2, 10, 0));
        when(experimentQuestionMapper.selectById(70L)).thenReturn(experimentQuestion());
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(experiment());
        when(disputeMapper.casResolve(eq(501L), eq("rubric"), any())).thenReturn(0);

        TeacherDisputeForwardResultVO vo = service.forward(forwardRequest(501L));

        assertEquals("already_withdrawn", vo.getResults().get(0).getStatus());
        verify(gradingQueuePublisher, never()).publishSubmission(anyLong());
    }

    private void stubOwnedOpen(long disputeId, Submission submission) {
        when(disputeMapper.selectById(disputeId)).thenReturn(ownedDispute(disputeId, submission.getId()));
        when(submissionMapper.selectById(submission.getId())).thenReturn(submission);
        lenient().when(experimentQuestionMapper.selectById(70L)).thenReturn(experimentQuestion());
        lenient().when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(experiment());
    }

    private static DisputeForwardRequest forwardRequest(Long... ids) {
        DisputeForwardRequest request = new DisputeForwardRequest();
        request.setNote("rubric");
        request.setDisputeIds(List.of(ids));
        return request;
    }

    private static DisputeResolveRequest commentOnly(String comment) {
        DisputeResolveRequest request = new DisputeResolveRequest();
        request.setManualComment(comment);
        return request;
    }

    private static DisputeResolveRequest withScore(String comment, long subQuestionId, int score) {
        DisputeResolveRequest request = commentOnly(comment);
        DisputeResolveScoreItem item = new DisputeResolveScoreItem();
        item.setSubQuestionId(subQuestionId);
        item.setManualScore(BigDecimal.valueOf(score));
        request.setSubQuestionScores(List.of(item));
        return request;
    }

    private static Dispute ownedDispute(long id, long submissionId) {
        Dispute d = new Dispute();
        d.setId(id);
        d.setSubmissionId(submissionId);
        d.setDisputeReason("误判");
        d.setDisputeTime(LocalDateTime.of(2026, 9, 9, 10, 0));
        return d;
    }

    private static Submission submission(long id, int status, Integer score, int override) {
        Submission s = new Submission();
        s.setId(id);
        s.setExperimentQuestionId(70L);
        s.setStatus(status);
        s.setScore(score);
        s.setIsManualOverride(override);
        s.setVersion(1);
        return s;
    }

    private static ExperimentQuestion experimentQuestion() {
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(70L);
        eq.setExperimentId(EXPERIMENT_ID);
        eq.setQuestionId(QUESTION_ID);
        return eq;
    }

    private static Experiment experiment() {
        Experiment e = new Experiment();
        e.setId(EXPERIMENT_ID);
        e.setUserId(TEACHER_ID);
        return e;
    }

    private static SubQuestion bank(long id, int max) {
        SubQuestion sq = new SubQuestion();
        sq.setId(id);
        sq.setQuestionId(QUESTION_ID);
        sq.setScore(max);
        return sq;
    }
}
