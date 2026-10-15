package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.StudentDisputeListRow;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.StudentDisputeListItemVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDisputeServiceImplTest {

    private static final long STUDENT_ID = 2L;
    private static final long OTHER_STUDENT_ID = 99L;

    @Mock
    private DisputeMapper disputeMapper;
    @Mock
    private SubmissionMapper submissionMapper;

    private StudentDisputeServiceImpl service;

    @BeforeEach
    void setUp() {
        TestAuth.setStudent(STUDENT_ID);
        service = new StudentDisputeServiceImpl(disputeMapper, submissionMapper);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void withdraw_isTransactional() throws NoSuchMethodException {
        assertTrue(StudentDisputeServiceImpl.class
                .getDeclaredMethod("withdraw", long.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void withdraw_open_setsWithdrawnTime() {
        Dispute open = ownedOpenDispute();
        when(disputeMapper.selectById(501L)).thenReturn(open);
        when(submissionMapper.selectById(900L)).thenReturn(ownedSubmission());
        when(disputeMapper.casWithdraw(eq(501L), any())).thenReturn(1);

        DisputeVO vo = service.withdraw(501L);

        assertEquals(501L, vo.getId());
        assertEquals(900L, vo.getSubmissionId());
        assertEquals(3, vo.getDisputeStatus());
        assertEquals("误提", vo.getDisputeReason());
        assertEquals(open.getDisputeTime(), vo.getDisputeTime());
        assertNull(vo.getManualComment());
        assertNull(vo.getResolveTime());
        assertEquals(vo.getWithdrawnTime(), open.getWithdrawnTime());
        verify(disputeMapper).casWithdraw(eq(501L), any());
        verify(submissionMapper, never()).updateById(any(Submission.class));
        verify(submissionMapper, never()).update(any(), any());
    }

    @Test
    void withdraw_resolved_isConflictAndDoesNotWrite() {
        Dispute resolved = ownedOpenDispute();
        resolved.setResolveTime(LocalDateTime.of(2026, 9, 8, 12, 0));
        when(disputeMapper.selectById(501L)).thenReturn(ownedOpenDispute(), resolved);
        when(submissionMapper.selectById(900L)).thenReturn(ownedSubmission());
        when(disputeMapper.casWithdraw(eq(501L), any())).thenReturn(0);

        ExperimentException ex = assertThrows(ExperimentException.class, () -> service.withdraw(501L));

        assertEquals("CONFLICT", ex.getCode());
        assertEquals("disputeStatus", ex.getFieldErrors().get(0).field());
        assertEquals("该申诉已被教师处理，无法撤回", ex.getMessage());
        verify(disputeMapper).casWithdraw(eq(501L), any());
    }

    @Test
    void withdraw_alreadyWithdrawn_isIdempotent() {
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 9, 8, 9, 0);
        Dispute withdrawn = ownedOpenDispute();
        withdrawn.setWithdrawnTime(withdrawnAt);
        when(disputeMapper.selectById(501L)).thenReturn(ownedOpenDispute(), withdrawn);
        when(submissionMapper.selectById(900L)).thenReturn(ownedSubmission());
        when(disputeMapper.casWithdraw(eq(501L), any())).thenReturn(0);

        DisputeVO vo = service.withdraw(501L);

        assertEquals(3, vo.getDisputeStatus());
        assertEquals(withdrawnAt, vo.getWithdrawnTime());
        verify(disputeMapper).casWithdraw(eq(501L), any());
    }

    @Test
    void withdraw_otherStudent_isNotFound() {
        when(disputeMapper.selectById(501L)).thenReturn(ownedOpenDispute());
        Submission other = ownedSubmission();
        other.setStudentId(OTHER_STUDENT_ID);
        when(submissionMapper.selectById(900L)).thenReturn(other);

        ExperimentException ex = assertThrows(ExperimentException.class, () -> service.withdraw(501L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).casWithdraw(anyLong(), any());
    }

    @Test
    void withdraw_missingId_isNotFound() {
        when(disputeMapper.selectById(501L)).thenReturn(null);

        ExperimentException ex = assertThrows(ExperimentException.class, () -> service.withdraw(501L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(disputeMapper, never()).casWithdraw(anyLong(), any());
    }

    @Test
    void withdraw_casLostToResolve_isConflict() {
        Dispute resolved = ownedOpenDispute();
        resolved.setResolveTime(LocalDateTime.of(2026, 9, 8, 12, 0));
        when(disputeMapper.selectById(501L)).thenReturn(ownedOpenDispute(), resolved);
        when(submissionMapper.selectById(900L)).thenReturn(ownedSubmission());
        when(disputeMapper.casWithdraw(eq(501L), any())).thenReturn(0);

        ExperimentException ex = assertThrows(ExperimentException.class, () -> service.withdraw(501L));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void withdraw_casLostToPriorWithdraw_isIdempotent200() {
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 9, 8, 9, 0);
        Dispute withdrawn = ownedOpenDispute();
        withdrawn.setWithdrawnTime(withdrawnAt);
        when(disputeMapper.selectById(501L)).thenReturn(ownedOpenDispute(), withdrawn);
        when(submissionMapper.selectById(900L)).thenReturn(ownedSubmission());
        when(disputeMapper.casWithdraw(eq(501L), any())).thenReturn(0);

        DisputeVO vo = service.withdraw(501L);

        assertEquals(3, vo.getDisputeStatus());
        assertEquals(withdrawnAt, vo.getWithdrawnTime());
    }

    @Test
    void listDisputes_empty_skipsPageQuery() {
        when(disputeMapper.countStudentDisputes(STUDENT_ID)).thenReturn(0L);

        PageResult<StudentDisputeListItemVO> result = service.listDisputes(1, 10);

        assertEquals(0, result.getTotal());
        assertEquals(List.of(), result.getData());
        verify(disputeMapper, never()).findStudentDisputes(STUDENT_ID, 10, 0);
    }

    @Test
    void listDisputes_twoFilings_newestFirst_openIsStatusOne() {
        when(disputeMapper.countStudentDisputes(STUDENT_ID)).thenReturn(2L);
        LocalDateTime newer = LocalDateTime.of(2026, 9, 8, 10, 15);
        LocalDateTime older = LocalDateTime.of(2026, 9, 7, 9, 0);
        when(disputeMapper.findStudentDisputes(STUDENT_ID, 10, 0)).thenReturn(List.of(
                listRow(502L, 901L, 19L, 42L, newer, 1, 2, 4),
                listRow(501L, 900L, 18L, 41L, older, 1, 2, 3)));

        PageResult<StudentDisputeListItemVO> result = service.listDisputes(1, 10);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getData().size());
        StudentDisputeListItemVO first = result.getData().get(0);
        assertEquals(502L, first.getId());
        assertEquals(901L, first.getSubmissionId());
        assertEquals(19L, first.getExperimentId());
        assertEquals("Linux 基础作业二", first.getExperimentTitle());
        assertEquals(42L, first.getExperimentQuestionId());
        assertEquals("管道", first.getQuestionTitle());
        assertEquals(1, first.getDisputeStatus());
        assertEquals(newer, first.getDisputeTime());
        assertEquals(4, first.getScore());
        assertEquals(501L, result.getData().get(1).getId());
        assertEquals(1, result.getData().get(1).getDisputeStatus());
        verify(disputeMapper).countStudentDisputes(STUDENT_ID);
        verify(disputeMapper, never()).countStudentDisputes(OTHER_STUDENT_ID);
    }

    @Test
    void listDisputes_afterSimulatedResolve_isStatusTwoAndKeepsScore() {
        when(disputeMapper.countStudentDisputes(STUDENT_ID)).thenReturn(1L);
        StudentDisputeListRow resolved = listRow(
                501L, 900L, 18L, 41L,
                LocalDateTime.of(2026, 9, 8, 10, 15), 2, 2, 3);
        when(disputeMapper.findStudentDisputes(STUDENT_ID, 10, 0)).thenReturn(List.of(resolved));

        StudentDisputeListItemVO item = service.listDisputes(1, 10).getData().get(0);

        assertEquals(2, item.getDisputeStatus());
        assertEquals(3, item.getScore());
    }

    @Test
    void listDisputes_withdrawn_isStatusThreeAndStaysVisible() {
        when(disputeMapper.countStudentDisputes(STUDENT_ID)).thenReturn(1L);
        when(disputeMapper.findStudentDisputes(STUDENT_ID, 10, 0)).thenReturn(List.of(
                listRow(501L, 900L, 18L, 41L,
                        LocalDateTime.of(2026, 9, 8, 10, 15), 3, 2, 3)));

        StudentDisputeListItemVO item = service.listDisputes(1, 10).getData().get(0);

        assertEquals(3, item.getDisputeStatus());
        assertEquals(3, item.getScore());
        assertEquals(501L, item.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void listDisputes_scoreIsNullWhenSubmissionNotComplete(int status) {
        when(disputeMapper.countStudentDisputes(STUDENT_ID)).thenReturn(1L);
        StudentDisputeListRow row = listRow(
                501L, 900L, 18L, 41L,
                LocalDateTime.of(2026, 9, 8, 10, 15), 1, status, 3);
        when(disputeMapper.findStudentDisputes(STUDENT_ID, 10, 0)).thenReturn(List.of(row));

        StudentDisputeListItemVO item = service.listDisputes(1, 10).getData().get(0);

        assertNull(item.getScore());
        assertEquals(1, item.getDisputeStatus());
    }

    @Test
    void listDisputes_newlyFiledRow_isVisibleAsOpen() {
        when(disputeMapper.countStudentDisputes(STUDENT_ID)).thenReturn(1L);
        when(disputeMapper.findStudentDisputes(STUDENT_ID, 10, 0)).thenReturn(List.of(
                listRow(501L, 900L, 18L, 41L,
                        LocalDateTime.of(2026, 9, 8, 10, 15), 1, 2, 3)));

        PageResult<StudentDisputeListItemVO> result = service.listDisputes(1, 10);

        assertEquals(1, result.getTotal());
        StudentDisputeListItemVO item = result.getData().get(0);
        assertEquals(501L, item.getId());
        assertEquals(900L, item.getSubmissionId());
        assertEquals(18L, item.getExperimentId());
        assertEquals(1, item.getDisputeStatus());
        assertEquals(3, item.getScore());
    }

    @Test
    void scoreForStatus_onlyStatusTwoKeepsScore() {
        assertEquals(3, StudentDisputeServiceImpl.scoreForStatus(2, 3));
        assertNull(StudentDisputeServiceImpl.scoreForStatus(0, 3));
        assertNull(StudentDisputeServiceImpl.scoreForStatus(1, 3));
        assertNull(StudentDisputeServiceImpl.scoreForStatus(3, 3));
        assertNull(StudentDisputeServiceImpl.scoreForStatus(null, 3));
    }

    private static Dispute ownedOpenDispute() {
        Dispute dispute = new Dispute();
        dispute.setId(501L);
        dispute.setSubmissionId(900L);
        dispute.setDisputeReason("误提");
        dispute.setDisputeTime(LocalDateTime.of(2026, 9, 8, 10, 15));
        dispute.setIsDelete(0);
        return dispute;
    }

    private static Submission ownedSubmission() {
        Submission submission = new Submission();
        submission.setId(900L);
        submission.setStudentId(STUDENT_ID);
        return submission;
    }

    private static StudentDisputeListRow listRow(
            long id, long submissionId, long experimentId, long experimentQuestionId,
            LocalDateTime disputeTime, int disputeStatus, int submissionStatus, Integer score) {
        StudentDisputeListRow row = new StudentDisputeListRow();
        row.setId(id);
        row.setSubmissionId(submissionId);
        row.setExperimentId(experimentId);
        row.setExperimentTitle(experimentId == 19L ? "Linux 基础作业二" : "Linux 基础作业一");
        row.setExperimentQuestionId(experimentQuestionId);
        row.setQuestionTitle(experimentQuestionId == 42L ? "管道" : "文件权限");
        row.setDisputeStatus(disputeStatus);
        row.setDisputeTime(disputeTime);
        row.setSubmissionStatus(submissionStatus);
        row.setScore(score);
        return row;
    }
}
