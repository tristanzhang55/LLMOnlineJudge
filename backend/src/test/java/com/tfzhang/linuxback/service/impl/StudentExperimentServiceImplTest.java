package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.dto.DisputeCreateRequest;
import com.tfzhang.linuxback.dto.StudentSubmitAnswerRequest;
import com.tfzhang.linuxback.dto.StudentSubmitRequest;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.SubmissionDraftService;
import com.tfzhang.linuxback.service.SubmissionQuery;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.ConfirmSubmitVO;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.MyExperimentScoresVO;
import com.tfzhang.linuxback.vo.MyQuestionScoresVO;
import com.tfzhang.linuxback.vo.MySubQuestionScoreVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentExperimentServiceImplTest {

    private static final long STUDENT_ID = 2L;

    @Mock
    private ExperimentMapper experimentMapper;
    @Mock
    private ExperimentQuestionMapper experimentQuestionMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private SubQuestionMapper subQuestionMapper;
    @Mock
    private SubQuestionScoreMapper subQuestionScoreMapper;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private DisputeMapper disputeMapper;
    @Mock
    private SubmissionQuery submissionQuery;
    @Mock
    private SubmissionDraftService submissionDraftService;
    @Mock
    private ExperimentService experimentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StudentExperimentServiceImpl studentExperimentService;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setStudent(STUDENT_ID);
        studentExperimentService = new StudentExperimentServiceImpl(
                experimentMapper,
                experimentQuestionMapper,
                classStudentMapper,
                questionMapper,
                subQuestionMapper,
                subQuestionScoreMapper,
                submissionMapper,
                disputeMapper,
                submissionQuery,
                submissionDraftService,
                experimentService,
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void finalizeSubmit_upsertsDraftThenSetsStatusZero() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        stubEnrollment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(experimentQuestion(41L)));
        when(submissionQuery.isLocked(5L, STUDENT_ID)).thenReturn(false);
        when(submissionDraftService.persistDraftRow(eq(41L), eq(STUDENT_ID), any())).thenReturn(900L);

        ConfirmSubmitVO result = studentExperimentService.finalizeSubmit(5L, submitRequest());

        assertEquals(1, result.getSubmissions().size());
        assertEquals(41L, result.getSubmissions().get(0).getExperimentQuestionId());
        assertEquals(900L, result.getSubmissions().get(0).getSubmissionId());
        assertNotNull(result.getSubmitTime());
        verify(submissionMapper).update(isNull(), any());
        verify(submissionMapper, never()).insert(any(Submission.class));
        verify(submissionDraftService).persistDraftRow(eq(41L), eq(STUDENT_ID), any());
        verify(submissionMapper, never()).deleteById(anyLong());
    }

    @Test
    void finalizeSubmit_softDeletesUnusedDrafts() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        stubEnrollment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                experimentQuestion(41L), experimentQuestion(42L)));
        when(submissionQuery.isLocked(5L, STUDENT_ID)).thenReturn(false);
        when(submissionDraftService.persistDraftRow(eq(41L), eq(STUDENT_ID), any())).thenReturn(900L);
        Submission unused = new Submission();
        unused.setId(88L);
        when(submissionMapper.findDraft(42L, STUDENT_ID)).thenReturn(unused);

        studentExperimentService.finalizeSubmit(5L, submitRequest());

        verify(submissionMapper).deleteById(88L);
    }

    @Test
    void finalizeSubmit_whenAlreadyLocked_throwsConflict() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        stubEnrollment();
        when(submissionQuery.isLocked(5L, STUDENT_ID)).thenReturn(true);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.finalizeSubmit(5L, submitRequest()));

        assertEquals("CONFLICT", ex.getCode());
        verify(submissionDraftService, never()).persistDraftRow(anyLong(), anyLong(), any());
    }

    @Test
    void finalizeSubmit_whenNotEnrolled_throwsNotFound() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.finalizeSubmit(5L, submitRequest()));

        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void getMyScores_whenNotEnrolled_throwsNotFound() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.getMyScores(5L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).markRepliesRead(anyLong(), anyLong(), any());
    }

    @Test
    void getMyScores_success_marksRepliesRead() {
        stubVisibleExperiment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of());
        when(submissionQuery.isRoundGradingComplete(5L, STUDENT_ID)).thenReturn(false);

        studentExperimentService.getMyScores(5L);

        verify(disputeMapper).markRepliesRead(eq(STUDENT_ID), eq(5L), any(LocalDateTime.class));
    }

    @Test
    void getMyScores_statusTwo_mapsEffectiveScoreAndAiEvidence() throws Exception {
        stubVisibleExperiment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(experimentQuestion(41L)));
        when(submissionQuery.isRoundGradingComplete(5L, STUDENT_ID)).thenReturn(true);
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(currentSubmission(900L, 2, 3));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bankSubQuestion(101L, 0, 4)));
        SubQuestionScore scoreRow = new SubQuestionScore();
        scoreRow.setSubQuestionId(101L);
        scoreRow.setEffectiveScore(new BigDecimal("3"));
        scoreRow.setAiEvidence("端口与参考步骤一致，扣 1 分因未写验证命令");
        scoreRow.setGradingLog("secret");
        when(subQuestionScoreMapper.selectList(any())).thenReturn(List.of(scoreRow));

        MyExperimentScoresVO vo = studentExperimentService.getMyScores(5L);

        assertEquals(5L, vo.getExperimentId());
        assertTrue(vo.getIsRoundGradingComplete());
        MyQuestionScoresVO q = vo.getQuestions().get(0);
        assertEquals(41L, q.getExperimentQuestionId());
        assertEquals(900L, q.getSubmissionId());
        assertEquals(2, q.getStatus());
        assertEquals(3, q.getScore());
        MySubQuestionScoreVO sq = q.getSubQuestions().get(0);
        assertEquals(101L, sq.getSubQuestionId());
        assertEquals(4, sq.getMaxScore());
        assertEquals(3, sq.getEffectiveScore());
        assertEquals("端口与参考步骤一致，扣 1 分因未写验证命令", sq.getComment());
        String json = objectMapper.writeValueAsString(vo);
        assertTrue(!json.contains("gradingLog"));
        assertTrue(!json.contains("referenceAnswer"));
        assertTrue(!json.contains("rubric"));
        assertTrue(!json.contains("secret"));
    }

    @Test
    void getMyScores_mapsLatestDisputeRow() {
        stubVisibleExperiment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(experimentQuestion(41L)));
        when(submissionQuery.isRoundGradingComplete(5L, STUDENT_ID)).thenReturn(true);
        Submission current = currentSubmission(900L, 2, 3);
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(current);
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bankSubQuestion(101L, 0, 4)));
        when(subQuestionScoreMapper.selectList(any())).thenReturn(List.of());
        LocalDateTime filedAt = LocalDateTime.of(2026, 9, 7, 20, 15);
        Dispute latest = new Dispute();
        latest.setId(501L);
        latest.setSubmissionId(900L);
        latest.setDisputeReason("第二小题沙箱输出判错，命令实际已成功");
        latest.setDisputeTime(filedAt);
        latest.setManualComment(null);
        when(disputeMapper.selectOne(any())).thenReturn(latest);

        MyQuestionScoresVO q = studentExperimentService.getMyScores(5L).getQuestions().get(0);

        assertEquals(1, q.getDisputeStatus());
        assertEquals(501L, q.getDisputeId());
        assertEquals("第二小题沙箱输出判错，命令实际已成功", q.getDisputeReason());
        assertEquals(filedAt, q.getDisputeTime());
        assertNull(q.getManualComment());
        assertNull(q.getDisputeResolveTime());
    }

    @Test
    void getMyScores_withdrawnLatestRow_isStatusThree() {
        stubVisibleExperiment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(experimentQuestion(41L)));
        when(submissionQuery.isRoundGradingComplete(5L, STUDENT_ID)).thenReturn(true);
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(currentSubmission(900L, 2, 3));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bankSubQuestion(101L, 0, 4)));
        when(subQuestionScoreMapper.selectList(any())).thenReturn(List.of());
        LocalDateTime filedAt = LocalDateTime.of(2026, 9, 7, 20, 15);
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 9, 8, 9, 0);
        Dispute latest = new Dispute();
        latest.setId(501L);
        latest.setSubmissionId(900L);
        latest.setDisputeReason("误提");
        latest.setDisputeTime(filedAt);
        latest.setWithdrawnTime(withdrawnAt);
        when(disputeMapper.selectOne(any())).thenReturn(latest);

        MyQuestionScoresVO q = studentExperimentService.getMyScores(5L).getQuestions().get(0);

        assertEquals(3, q.getDisputeStatus());
        assertEquals(501L, q.getDisputeId());
        assertEquals(filedAt, q.getDisputeTime());
        assertNull(q.getDisputeResolveTime());
    }

    @Test
    void fileDispute_isTransactional() throws NoSuchMethodException {
        assertTrue(StudentExperimentServiceImpl.class
                .getDeclaredMethod("fileDispute", long.class, long.class, DisputeCreateRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void fileDispute_happyPath_writesDisputeRow(int status) {
        stubFileableSubmission(status);
        stubInsertAssignsId();

        DisputeVO vo = studentExperimentService.fileDispute(5L, 900L, disputeRequest());

        assertEquals(501L, vo.getId());
        assertEquals(900L, vo.getSubmissionId());
        assertEquals(1, vo.getDisputeStatus());
        assertEquals("第二小题沙箱输出判错，命令实际已成功", vo.getDisputeReason());
        assertNotNull(vo.getDisputeTime());
        assertNull(vo.getManualComment());
        assertNull(vo.getResolveTime());
        assertNull(vo.getWithdrawnTime());

        ArgumentCaptor<Dispute> inserted = ArgumentCaptor.forClass(Dispute.class);
        verify(disputeMapper).insert(inserted.capture());
        assertEquals(900L, inserted.getValue().getSubmissionId());
        assertEquals("第二小题沙箱输出判错，命令实际已成功", inserted.getValue().getDisputeReason());
        assertEquals(0, inserted.getValue().getIsDelete());
        assertNull(inserted.getValue().getManualComment());
        assertNull(inserted.getValue().getResolveTime());
        assertNull(inserted.getValue().getWithdrawnTime());
        verify(submissionMapper, never()).update(isNull(), any());
        verify(experimentService).persistExpiredPublished(5L, null);
    }

    @Test
    void fileDispute_secondOpenRound_isConflict() {
        Submission locked = stubOwnedCurrentSubmission(2);
        when(submissionMapper.selectOne(any())).thenReturn(locked);
        Dispute open = new Dispute();
        open.setId(501L);
        open.setSubmissionId(900L);
        when(disputeMapper.selectOne(any())).thenReturn(open);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.fileDispute(5L, 900L, disputeRequest()));

        assertEquals("CONFLICT", ex.getCode());
        assertEquals("disputeStatus", ex.getFieldErrors().get(0).field());
        verify(disputeMapper, never()).insert(any(Dispute.class));
        verify(submissionMapper, never()).update(isNull(), any());
    }

    @Test
    void fileDispute_afterSimulatedResolve_filesNewRound() {
        stubFileableSubmission(2);
        stubInsertAssignsId();

        DisputeVO vo = studentExperimentService.fileDispute(5L, 900L, disputeRequest());

        assertEquals(501L, vo.getId());
        assertEquals(1, vo.getDisputeStatus());
        assertNull(vo.getWithdrawnTime());
        verify(disputeMapper).insert(any(Dispute.class));
    }

    @Test
    void fileDispute_afterWithdrawnRound_filesNewRound() {
        stubFileableSubmission(2);
        stubInsertAssignsId();

        DisputeVO vo = studentExperimentService.fileDispute(5L, 900L, disputeRequest());

        assertEquals(1, vo.getDisputeStatus());
        verify(disputeMapper).insert(any(Dispute.class));
        verify(submissionMapper, never()).update(isNull(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1})
    void fileDispute_whenNotGraded_isInvalidParameter(int status) {
        stubOwnedCurrentSubmission(status);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.fileDispute(5L, 900L, disputeRequest()));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("status", ex.getFieldErrors().get(0).field());
        verify(disputeMapper, never()).insert(any(Dispute.class));
    }

    @Test
    void fileDispute_whenWithdrawn_throwsNotFound() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        experiment.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(experiment);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.fileDispute(5L, 900L, disputeRequest()));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(submissionMapper, never()).selectById(anyLong());
    }

    @Test
    void fileDispute_whenNotEnrolled_throwsNotFound() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.fileDispute(5L, 900L, disputeRequest()));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(submissionMapper, never()).selectById(anyLong());
    }

    @Test
    void fileDispute_missingSubmission_throwsNotFound() {
        stubVisibleExperiment();
        when(submissionMapper.selectById(900L)).thenReturn(null);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.fileDispute(5L, 900L, disputeRequest()));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).insert(any(Dispute.class));
    }

    @Test
    void fileDispute_otherStudentsSubmission_throwsNotFound() {
        stubVisibleExperiment();
        Submission submission = currentSubmission(900L, 2, 3);
        submission.setStudentId(99L);
        submission.setExperimentQuestionId(41L);
        when(submissionMapper.selectById(900L)).thenReturn(submission);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.fileDispute(5L, 900L, disputeRequest()));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).insert(any(Dispute.class));
    }

    @Test
    void fileDispute_priorRoundSubmission_throwsNotFound() {
        stubOwnedCurrentSubmission(2);
        Submission current = currentSubmission(901L, 2, 3);
        current.setStudentId(STUDENT_ID);
        current.setExperimentQuestionId(41L);
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(current);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.fileDispute(5L, 900L, disputeRequest()));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).insert(any(Dispute.class));
    }

    @Test
    void fileDispute_whenExpiredPublished_succeeds() {
        stubFileableSubmission(2);
        Experiment expired = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        expired.setStatus(2);
        when(experimentMapper.selectById(5L)).thenReturn(expired);
        stubInsertAssignsId();

        DisputeVO vo = studentExperimentService.fileDispute(5L, 900L, disputeRequest());

        assertEquals(501L, vo.getId());
        verify(disputeMapper).insert(any(Dispute.class));
    }

    @Test
    void finalizeSubmit_whenAssignmentDraft_throwsNotFound() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        experiment.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(experiment);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.finalizeSubmit(5L, submitRequest()));

        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void getMyScores_whenUnpublished_throwsNotFound() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        experiment.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(experiment);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.getMyScores(5L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).markRepliesRead(anyLong(), anyLong(), any());
    }

    @Test
    void getMyScores_whenMissingId_throwsNotFound() {
        when(experimentMapper.selectById(5L)).thenReturn(null);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                studentExperimentService.getMyScores(5L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).markRepliesRead(anyLong(), anyLong(), any());
    }

    @Test
    void getMyScores_statusThree_returnsEmptySubQuestions() {
        stubVisibleExperiment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(experimentQuestion(41L)));
        when(submissionQuery.isRoundGradingComplete(5L, STUDENT_ID)).thenReturn(true);
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(currentSubmission(901L, 3, null));

        MyExperimentScoresVO vo = studentExperimentService.getMyScores(5L);

        MyQuestionScoresVO q = vo.getQuestions().get(0);
        assertEquals(901L, q.getSubmissionId());
        assertEquals(3, q.getStatus());
        assertNull(q.getScore());
        assertEquals(0, q.getDisputeStatus());
        assertNull(q.getDisputeId());
        assertNull(q.getDisputeReason());
        assertEquals(List.of(), q.getSubQuestions());
        verify(subQuestionScoreMapper, never()).selectList(any());
    }

    @Test
    void getMyScores_statusZero_doesNotReturnHalfGrading() {
        stubVisibleExperiment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(experimentQuestion(41L)));
        when(submissionQuery.isRoundGradingComplete(5L, STUDENT_ID)).thenReturn(false);
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(currentSubmission(900L, 0, null));

        MyQuestionScoresVO q = studentExperimentService.getMyScores(5L).getQuestions().get(0);

        assertEquals(0, q.getStatus());
        assertNull(q.getScore());
        assertEquals(List.of(), q.getSubQuestions());
        verify(subQuestionScoreMapper, never()).selectList(any());
    }

    @Test
    void getMyScores_statusOne_doesNotReturnHalfGrading() {
        stubVisibleExperiment();
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(experimentQuestion(41L)));
        when(submissionQuery.isRoundGradingComplete(5L, STUDENT_ID)).thenReturn(false);
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(currentSubmission(900L, 1, null));

        MyQuestionScoresVO q = studentExperimentService.getMyScores(5L).getQuestions().get(0);

        assertEquals(1, q.getStatus());
        assertNull(q.getScore());
        assertEquals(List.of(), q.getSubQuestions());
        verify(subQuestionScoreMapper, never()).selectList(any());
    }

    private static StudentSubmitRequest submitRequest() {
        StudentSubmitRequest request = new StudentSubmitRequest();
        StudentSubmitAnswerRequest answer = new StudentSubmitAnswerRequest();
        answer.setExperimentQuestionId(41L);
        answer.setAnswerBlocks(JsonNodeFactory.instance.arrayNode());
        request.setAnswers(List.of(answer));
        return request;
    }

    private static DisputeCreateRequest disputeRequest() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setDisputeReason("第二小题沙箱输出判错，命令实际已成功");
        return request;
    }

    private Submission stubOwnedCurrentSubmission(int status) {
        stubVisibleExperiment();
        Submission submission = currentSubmission(900L, status, status == 2 ? 3 : null);
        submission.setStudentId(STUDENT_ID);
        submission.setExperimentQuestionId(41L);
        when(submissionMapper.selectById(900L)).thenReturn(submission);
        when(experimentQuestionMapper.selectById(41L)).thenReturn(experimentQuestion(41L));
        when(submissionQuery.getCurrentSubmission(41L, STUDENT_ID)).thenReturn(submission);
        return submission;
    }

    private Submission stubFileableSubmission(int status) {
        Submission submission = stubOwnedCurrentSubmission(status);
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        when(disputeMapper.selectOne(any())).thenReturn(null);
        return submission;
    }

    private void stubInsertAssignsId() {
        doAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            dispute.setId(501L);
            return 1;
        }).when(disputeMapper).insert(any(Dispute.class));
    }

    private void stubEnrollment() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
    }

    private void stubVisibleExperiment() {
        Experiment experiment = publishedExperiment(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        stubEnrollment();
    }

    private static Submission currentSubmission(long id, int status, Integer score) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setStatus(status);
        submission.setScore(score);
        return submission;
    }

    private static SubQuestion bankSubQuestion(long id, int orderIndex, int maxScore) {
        SubQuestion sq = new SubQuestion();
        sq.setId(id);
        sq.setQuestionId(12L);
        sq.setOrderIndex(orderIndex);
        sq.setScore(maxScore);
        return sq;
    }

    private static ExperimentQuestion experimentQuestion(long id) {
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(id);
        eq.setExperimentId(5L);
        eq.setQuestionId(12L);
        return eq;
    }

    private static Experiment publishedExperiment(LocalDateTime lastPublishedTime) {
        Experiment experiment = new Experiment();
        experiment.setId(5L);
        experiment.setTitle("Linux 基础作业一");
        experiment.setDescription("desc");
        experiment.setClassId(1L);
        experiment.setStatus(1);
        experiment.setLastPublishedTime(lastPublishedTime);
        experiment.setDeadlineTime(LocalDateTime.of(2026, 12, 31, 23, 59));
        return experiment;
    }
}
