package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import com.tfzhang.linuxback.dto.TeacherDisputeInboxRow;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.TeacherDisputeInboxItemVO;
import com.tfzhang.linuxback.vo.TeacherStudentSubmissionsVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionQuestionVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionRoundItemVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionSubQuestionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherSubmissionServiceImplTest {

    private static final long TEACHER_ID = 9L;
    private static final long STUDENT_ID = 4021L;
    private static final long EXPERIMENT_ID = 18L;

    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private DisputeMapper disputeMapper;
    @Mock
    private ExperimentService experimentService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;
    @Mock
    private ExperimentQuestionMapper experimentQuestionMapper;
    @Mock
    private SubQuestionMapper subQuestionMapper;
    @Mock
    private SubQuestionScoreMapper subQuestionScoreMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TeacherSubmissionServiceImpl service;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_ID);
        service = new TeacherSubmissionServiceImpl(
                submissionMapper,
                disputeMapper,
                experimentService,
                userMapper,
                classStudentMapper,
                experimentQuestionMapper,
                subQuestionMapper,
                subQuestionScoreMapper,
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void toLikePattern_blankOrNull_isNull() {
        assertNull(TeacherSubmissionServiceImpl.toLikePattern(null));
        assertNull(TeacherSubmissionServiceImpl.toLikePattern(""));
        assertNull(TeacherSubmissionServiceImpl.toLikePattern("   "));
    }

    @Test
    void toLikePattern_trimsAndEscapesWildcards() {
        assertEquals("%100\\%\\_x%", TeacherSubmissionServiceImpl.toLikePattern("  100%_x "));
    }

    @Test
    void listRounds_empty_skipsPageQuery() {
        when(submissionMapper.countTeacherSubmissionRounds(TEACHER_ID, null, null)).thenReturn(0L);

        PageResult<TeacherSubmissionRoundItemVO> result = service.listRounds(1, 10, null, null);

        assertEquals(0, result.getTotal());
        assertEquals(List.of(), result.getData());
        verify(submissionMapper, never()).findTeacherSubmissionRounds(
                eq(TEACHER_ID), any(), any(), eq(10), eq(0));
    }

    @Test
    void listRounds_blankStudentName_doesNotPassLike() {
        when(submissionMapper.countTeacherSubmissionRounds(TEACHER_ID, null, null)).thenReturn(0L);

        service.listRounds(1, 10, "   ", null);

        verify(submissionMapper).countTeacherSubmissionRounds(TEACHER_ID, null, null);
    }

    @Test
    void listRounds_nonBlankStudentName_passesEscapedLike() {
        when(submissionMapper.countTeacherSubmissionRounds(TEACHER_ID, null, "%Lin\\%ux%")).thenReturn(0L);

        service.listRounds(1, 10, "Lin%ux", null);

        verify(submissionMapper).countTeacherSubmissionRounds(TEACHER_ID, null, "%Lin\\%ux%");
    }

    @Test
    void listRounds_classId_passedThroughAndAndCombinedWithName() {
        String like = "%张%";
        when(submissionMapper.countTeacherSubmissionRounds(TEACHER_ID, 2, like)).thenReturn(0L);

        service.listRounds(1, 10, "张", 2);

        verify(submissionMapper).countTeacherSubmissionRounds(TEACHER_ID, 2, like);
    }

    @Test
    void listRounds_usesOffsetAndTotalFromCount() {
        when(submissionMapper.countTeacherSubmissionRounds(TEACHER_ID, null, null)).thenReturn(37L);
        SubmissionRoundAggregateRow row = new SubmissionRoundAggregateRow();
        row.setExperimentId(EXPERIMENT_ID);
        row.setExperimentTitle("Linux 基础作业一");
        row.setClassId(2L);
        row.setStudentId(STUDENT_ID);
        row.setStudentName("Alice");
        row.setSubmitTime(LocalDateTime.of(2026, 9, 1, 10, 15));
        row.setQuestionCount(4L);
        row.setPendingOrGradingCount(0L);
        row.setGradingCount(0L);
        row.setDoneCount(4L);
        row.setFailedCount(0L);
        row.setScoreSum(15L);
        when(submissionMapper.findTeacherSubmissionRounds(TEACHER_ID, null, null, 10, 10))
                .thenReturn(List.of(row));

        PageResult<TeacherSubmissionRoundItemVO> result = service.listRounds(2, 10, null, null);

        assertEquals(37, result.getTotal());
        assertEquals(1, result.getData().size());
        TeacherSubmissionRoundItemVO item = result.getData().get(0);
        assertEquals(EXPERIMENT_ID, item.getExperimentId());
        assertEquals("Linux 基础作业一", item.getExperimentTitle());
        assertEquals(2L, item.getClassId());
        assertEquals(STUDENT_ID, item.getStudentId());
        assertEquals("Alice", item.getStudentName());
        assertEquals(4, item.getQuestionCount());
        assertEquals("completed", item.getStatus());
        assertEquals(15, item.getScore());
    }

    @Test
    void listRounds_pending_scoreIsNull() {
        when(submissionMapper.countTeacherSubmissionRounds(TEACHER_ID, null, null)).thenReturn(1L);
        SubmissionRoundAggregateRow row = new SubmissionRoundAggregateRow();
        row.setQuestionCount(3L);
        row.setPendingOrGradingCount(3L);
        row.setGradingCount(0L);
        row.setDoneCount(0L);
        row.setFailedCount(0L);
        row.setScoreSum(9L);
        when(submissionMapper.findTeacherSubmissionRounds(TEACHER_ID, null, null, 10, 0))
                .thenReturn(List.of(row));

        TeacherSubmissionRoundItemVO item = service.listRounds(1, 10, null, null).getData().get(0);

        assertEquals("pending", item.getStatus());
        assertNull(item.getScore());
    }

    @Test
    void getStudentSubmissions_unownedExperiment_throwsNotFound() {
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID))
                .thenThrow(ExperimentException.notFound());

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                service.getStudentSubmissions(EXPERIMENT_ID, STUDENT_ID));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void getStudentSubmissions_missingStudent_throwsNotFound() {
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(ownedExperiment());
        when(userMapper.selectById(STUDENT_ID)).thenReturn(null);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                service.getStudentSubmissions(EXPERIMENT_ID, STUDENT_ID));

        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void getStudentSubmissions_notEnrolled_throwsNotFound() {
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(ownedExperiment());
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student());
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                service.getStudentSubmissions(EXPERIMENT_ID, STUDENT_ID));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(submissionMapper, never()).findConfirmedByExperimentAndStudent(anyLong(), anyLong());
    }

    @Test
    void getStudentSubmissions_enrolledWithNoRounds_returnsEmptyRounds() {
        stubVisibleStudent();
        when(submissionMapper.findConfirmedByExperimentAndStudent(EXPERIMENT_ID, STUDENT_ID))
                .thenReturn(List.of());

        TeacherStudentSubmissionsVO vo = service.getStudentSubmissions(EXPERIMENT_ID, STUDENT_ID);

        assertEquals(EXPERIMENT_ID, vo.getExperimentId());
        assertEquals(STUDENT_ID, vo.getStudentId());
        assertEquals(List.of(), vo.getRounds());
        verify(experimentQuestionMapper, never()).selectList(any());
    }

    @Test
    void getStudentSubmissions_multipleRounds_newestFirstWithInternals() throws Exception {
        stubVisibleStudent();
        LocalDateTime newer = LocalDateTime.of(2026, 9, 1, 10, 15);
        LocalDateTime older = LocalDateTime.of(2026, 8, 1, 9, 0);
        Submission newerRow = submission(900L, 41L, 2, 3, newer, "[{\"subQuestionId\":101}]");
        Submission olderRow = submission(800L, 41L, 2, 2, older, "[]");
        when(submissionMapper.findConfirmedByExperimentAndStudent(EXPERIMENT_ID, STUDENT_ID))
                .thenReturn(List.of(newerRow, olderRow));

        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setExperimentId(EXPERIMENT_ID);
        eq.setQuestionId(12L);
        eq.setSortOrder(0);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(eq));

        SubQuestion bank = new SubQuestion();
        bank.setId(101L);
        bank.setQuestionId(12L);
        bank.setOrderIndex(0);
        bank.setContent("open port");
        bank.setReferenceAnswer("ss -lnt");
        bank.setRubric("must mention listen");
        bank.setScore(4);
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bank));

        SubQuestionScore score = new SubQuestionScore();
        score.setSubmissionId(900L);
        score.setSubQuestionId(101L);
        score.setAiScore(BigDecimal.valueOf(3));
        score.setEffectiveScore(BigDecimal.valueOf(3));
        score.setUsedSandbox(1);
        score.setAiEvidence("matches");
        score.setGradingLog("sandbox ok");
        when(subQuestionScoreMapper.selectList(any())).thenReturn(List.of(score));

        TeacherStudentSubmissionsVO vo = service.getStudentSubmissions(EXPERIMENT_ID, STUDENT_ID);

        assertEquals(2, vo.getRounds().size());
        assertEquals(newer, vo.getRounds().get(0).getSubmitTime());
        assertEquals(older, vo.getRounds().get(1).getSubmitTime());

        TeacherSubmissionQuestionVO q = vo.getRounds().get(0).getQuestions().get(0);
        assertEquals(41L, q.getExperimentQuestionId());
        assertEquals(900L, q.getSubmissionId());
        assertEquals(2, q.getStatus());
        assertEquals(3, q.getScore());
        TeacherSubmissionSubQuestionVO sq = q.getSubQuestions().get(0);
        assertEquals(101L, sq.getSubQuestionId());
        assertEquals("open port", sq.getContent());
        assertEquals("ss -lnt", sq.getReferenceAnswer());
        assertEquals("must mention listen", sq.getRubric());
        assertEquals(4, sq.getMaxScore());
        assertEquals(3, sq.getAiScore());
        assertEquals(3, sq.getEffectiveScore());
        assertEquals(Boolean.TRUE, sq.getUsedSandbox());
        assertEquals("matches", sq.getAiEvidence());
        assertEquals("sandbox ok", sq.getGradingLog());
        assertTrue(sq.getStudentAnswerBlocks().isArray());
        assertNull(vo.getRounds().get(1).getQuestions().get(0).getSubQuestions().get(0).getAiScore());
    }

    @Test
    void getStudentSubmissions_disputesNewestFirstWithLatestScalars() {
        stubVisibleStudent();
        LocalDateTime newer = LocalDateTime.of(2026, 9, 7, 20, 15);
        LocalDateTime older = LocalDateTime.of(2026, 9, 1, 10, 0);
        Submission row = submission(900L, 41L, 2, 3, newer, "[]");
        when(submissionMapper.findConfirmedByExperimentAndStudent(EXPERIMENT_ID, STUDENT_ID))
                .thenReturn(List.of(row));
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setQuestionId(12L);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(eq));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of());
        when(subQuestionScoreMapper.selectList(any())).thenReturn(List.of());

        Dispute open = new Dispute();
        open.setId(502L);
        open.setSubmissionId(900L);
        open.setDisputeReason("second round");
        open.setDisputeTime(newer);
        Dispute resolved = new Dispute();
        resolved.setId(501L);
        resolved.setSubmissionId(900L);
        resolved.setDisputeReason("first round");
        resolved.setDisputeTime(older);
        resolved.setManualComment("upheld");
        resolved.setResolveTime(older.plusHours(1));
        Dispute withdrawn = new Dispute();
        withdrawn.setId(500L);
        withdrawn.setSubmissionId(900L);
        withdrawn.setDisputeReason("filed then withdrew");
        withdrawn.setDisputeTime(older.minusDays(1));
        withdrawn.setWithdrawnTime(older.minusHours(2));
        when(disputeMapper.selectList(any())).thenReturn(List.of(open, resolved, withdrawn));

        TeacherSubmissionQuestionVO q = service.getStudentSubmissions(EXPERIMENT_ID, STUDENT_ID)
                .getRounds().get(0).getQuestions().get(0);

        assertEquals(1, q.getDisputeStatus());
        assertEquals("second round", q.getDisputeReason());
        assertEquals(newer, q.getDisputeTime());
        assertNull(q.getManualComment());
        assertNull(q.getDisputeResolveTime());
        assertEquals(3, q.getDisputes().size());
        assertEquals(502L, q.getDisputes().get(0).getId());
        assertEquals("second round", q.getDisputes().get(0).getDisputeReason());
        assertNull(q.getDisputes().get(0).getResolveTime());
        assertNull(q.getDisputes().get(0).getWithdrawnTime());
        assertEquals(501L, q.getDisputes().get(1).getId());
        assertEquals("upheld", q.getDisputes().get(1).getManualComment());
        assertEquals(500L, q.getDisputes().get(2).getId());
        assertEquals(older.minusHours(2), q.getDisputes().get(2).getWithdrawnTime());
    }

    @Test
    void listDisputes_empty_skipsPageQuery() {
        when(disputeMapper.countTeacherDisputes(TEACHER_ID, null, null, null, 1)).thenReturn(0L);

        PageResult<TeacherDisputeInboxItemVO> result = service.listDisputes(1, 10, null, null, null, null);

        assertEquals(0, result.getTotal());
        assertEquals(List.of(), result.getData());
        verify(disputeMapper, never()).findTeacherDisputes(
                eq(TEACHER_ID), any(), any(), any(), any(), eq(10), eq(0));
    }

    @Test
    void listDisputes_defaultFilterIsOpenOnly() {
        when(disputeMapper.countTeacherDisputes(TEACHER_ID, null, null, null, 1)).thenReturn(0L);

        service.listDisputes(1, 10, null, null, null, null);

        verify(disputeMapper).countTeacherDisputes(TEACHER_ID, null, null, null, 1);
    }

    @Test
    void listDisputes_resolvedFilter_passedThrough() {
        when(disputeMapper.countTeacherDisputes(TEACHER_ID, null, null, null, 2)).thenReturn(0L);

        service.listDisputes(1, 10, null, null, null, 2);

        verify(disputeMapper).countTeacherDisputes(TEACHER_ID, null, null, null, 2);
    }

    @Test
    void listDisputes_invalidStatus_throwsBadRequest() {
        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                service.listDisputes(1, 10, null, null, null, 3));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        verifyNoInteractions(disputeMapper);
    }

    @Test
    void listDisputes_andCombinesNameClassAndExperiment() {
        String like = "%张%";
        when(disputeMapper.countTeacherDisputes(TEACHER_ID, 2, 18, like, 1)).thenReturn(0L);

        service.listDisputes(1, 10, "张", 2, 18, 1);

        verify(disputeMapper).countTeacherDisputes(TEACHER_ID, 2, 18, like, 1);
    }

    @Test
    void listDisputes_totalIsDisputeCountAndPreservesOrder() {
        when(disputeMapper.countTeacherDisputes(TEACHER_ID, null, null, null, 1)).thenReturn(12L);
        LocalDateTime newer = LocalDateTime.of(2026, 9, 7, 20, 15);
        LocalDateTime older = LocalDateTime.of(2026, 9, 6, 9, 0);
        TeacherDisputeInboxRow first = inboxRow(502L, newer);
        TeacherDisputeInboxRow second = inboxRow(501L, older);
        when(disputeMapper.findTeacherDisputes(TEACHER_ID, null, null, null, 1, 10, 0))
                .thenReturn(List.of(first, second));

        PageResult<TeacherDisputeInboxItemVO> result = service.listDisputes(1, 10, null, null, null, 1);

        assertEquals(12, result.getTotal());
        assertEquals(2, result.getData().size());
        assertEquals(502L, result.getData().get(0).getId());
        assertEquals(501L, result.getData().get(1).getId());
        TeacherDisputeInboxItemVO item = result.getData().get(0);
        assertEquals(900L, item.getSubmissionId());
        assertEquals(EXPERIMENT_ID, item.getExperimentId());
        assertEquals("Linux 基础作业一", item.getExperimentTitle());
        assertEquals(2L, item.getClassId());
        assertEquals(STUDENT_ID, item.getStudentId());
        assertEquals("Alice", item.getStudentName());
        assertEquals(41L, item.getExperimentQuestionId());
        assertEquals("open port", item.getQuestionTitle());
        assertEquals(2, item.getSubmissionStatus());
        assertEquals(3, item.getScore());
        assertEquals(1, item.getDisputeStatus());
        assertEquals("第二小题沙箱输出判错，命令实际已成功", item.getDisputeReason());
        assertNull(item.getManualComment());
        assertNull(item.getResolveTime());
        assertEquals(0, item.getIsManualOverride());
    }

    @Test
    void getStudentSubmissions_pendingQuestion_scoreNullButBankPresent() {
        stubVisibleStudent();
        Submission pending = submission(900L, 41L, 0, null, LocalDateTime.of(2026, 9, 1, 10, 15), "[]");
        when(submissionMapper.findConfirmedByExperimentAndStudent(EXPERIMENT_ID, STUDENT_ID))
                .thenReturn(List.of(pending));

        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setId(41L);
        eq.setQuestionId(12L);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(eq));
        SubQuestion bank = new SubQuestion();
        bank.setId(101L);
        bank.setQuestionId(12L);
        bank.setContent("q");
        bank.setReferenceAnswer("ref");
        bank.setScore(4);
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(bank));
        when(subQuestionScoreMapper.selectList(any())).thenReturn(List.of());

        TeacherSubmissionQuestionVO q = service.getStudentSubmissions(EXPERIMENT_ID, STUDENT_ID)
                .getRounds().get(0).getQuestions().get(0);

        assertEquals(0, q.getStatus());
        assertNull(q.getScore());
        assertEquals("ref", q.getSubQuestions().get(0).getReferenceAnswer());
        assertNull(q.getSubQuestions().get(0).getEffectiveScore());
    }

    private void stubVisibleStudent() {
        when(experimentService.requireOwnedExperiment(EXPERIMENT_ID)).thenReturn(ownedExperiment());
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student());
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
    }

    private static Experiment ownedExperiment() {
        Experiment experiment = new Experiment();
        experiment.setId(EXPERIMENT_ID);
        experiment.setClassId(2L);
        experiment.setUserId(TEACHER_ID);
        return experiment;
    }

    private static User student() {
        User user = new User();
        user.setId(STUDENT_ID);
        user.setUserName("Alice");
        return user;
    }

    private static Submission submission(
            long id, long experimentQuestionId, int status, Integer score,
            LocalDateTime submitTime, String answerBlocks) {
        Submission row = new Submission();
        row.setId(id);
        row.setExperimentQuestionId(experimentQuestionId);
        row.setStudentId(STUDENT_ID);
        row.setStatus(status);
        row.setScore(score);
        row.setSubmitTime(submitTime);
        row.setAnswerBlocks(answerBlocks);
        return row;
    }

    private static TeacherDisputeInboxRow inboxRow(long id, LocalDateTime disputeTime) {
        TeacherDisputeInboxRow row = new TeacherDisputeInboxRow();
        row.setId(id);
        row.setSubmissionId(900L);
        row.setExperimentId(EXPERIMENT_ID);
        row.setExperimentTitle("Linux 基础作业一");
        row.setClassId(2L);
        row.setStudentId(STUDENT_ID);
        row.setStudentName("Alice");
        row.setExperimentQuestionId(41L);
        row.setQuestionTitle("open port");
        row.setSubmissionStatus(2);
        row.setScore(3);
        row.setDisputeStatus(1);
        row.setDisputeReason("第二小题沙箱输出判错，命令实际已成功");
        row.setDisputeTime(disputeTime);
        row.setIsManualOverride(0);
        return row;
    }
}
