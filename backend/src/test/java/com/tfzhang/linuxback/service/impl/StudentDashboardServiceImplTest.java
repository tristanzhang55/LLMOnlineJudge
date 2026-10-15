package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.StudentDashboardSummaryVO;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDashboardServiceImplTest {

    private static final long STUDENT_ID = 2L;

    @Mock
    private ExperimentService experimentService;
    @Mock
    private ExperimentMapper experimentMapper;
    @Mock
    private DisputeMapper disputeMapper;

    private StudentDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        TestAuth.setStudent(STUDENT_ID);
        service = new StudentDashboardServiceImpl(experimentService, experimentMapper, disputeMapper);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void summaryVo_hasExactlyTwoCountFields() {
        Set<String> names = Arrays.stream(StudentDashboardSummaryVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("pendingAssignments", "disputesWithNewReply"), names);
        assertFalse(names.contains("userName"));
    }

    @Test
    void summary_empty_isZeros() {
        when(experimentMapper.countPendingAssignments(STUDENT_ID)).thenReturn(0L);
        when(disputeMapper.countUnreadTeacherReplies(
                STUDENT_ID, StudentDashboardServiceImpl.AUTO_CLOSE_NOTE)).thenReturn(0L);

        StudentDashboardSummaryVO vo = service.getSummary();
        assertEquals(0L, vo.getPendingAssignments());
        assertEquals(0L, vo.getDisputesWithNewReply());
        verify(disputeMapper, never()).markRepliesRead(anyLong(), anyLong(), any());
    }

    @Test
    void summary_mapsMapperCounts() {
        when(experimentMapper.countPendingAssignments(STUDENT_ID)).thenReturn(1L);
        when(disputeMapper.countUnreadTeacherReplies(
                STUDENT_ID, StudentDashboardServiceImpl.AUTO_CLOSE_NOTE)).thenReturn(2L);

        StudentDashboardSummaryVO vo = service.getSummary();
        assertEquals(1L, vo.getPendingAssignments());
        assertEquals(2L, vo.getDisputesWithNewReply());
    }

    @Test
    void summary_expiresPublishedBeforePendingCount() {
        when(experimentMapper.countPendingAssignments(STUDENT_ID)).thenReturn(0L);
        when(disputeMapper.countUnreadTeacherReplies(
                STUDENT_ID, StudentDashboardServiceImpl.AUTO_CLOSE_NOTE)).thenReturn(0L);

        service.getSummary();

        InOrder order = inOrder(experimentService, experimentMapper);
        order.verify(experimentService).persistExpiredPublished(null, null);
        order.verify(experimentMapper).countPendingAssignments(STUDENT_ID);
    }

    @Test
    void summary_usesExactAutoCloseNote() {
        assertEquals("[系统] 作业已重新发布，原申诉自动关闭", StudentDashboardServiceImpl.AUTO_CLOSE_NOTE);
        when(experimentMapper.countPendingAssignments(STUDENT_ID)).thenReturn(0L);
        when(disputeMapper.countUnreadTeacherReplies(
                STUDENT_ID, StudentDashboardServiceImpl.AUTO_CLOSE_NOTE)).thenReturn(0L);

        service.getSummary();

        verify(disputeMapper).countUnreadTeacherReplies(
                STUDENT_ID, "[系统] 作业已重新发布，原申诉自动关闭");
    }

    @Test
    void noPrincipal_isUnauthorized() {
        TestAuth.clear();
        AuthException ex = assertThrows(AuthException.class, () -> service.getSummary());
        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(experimentMapper, never()).countPendingAssignments(anyLong());
        verify(disputeMapper, never()).countUnreadTeacherReplies(anyLong(), any());
        verify(disputeMapper, never()).markRepliesRead(anyLong(), anyLong(), any());
    }

    @Test
    void pendingSql_publishedUnlockedOnly() throws Exception {
        Select select = ExperimentMapper.class
                .getMethod("countPendingAssignments", long.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());
        assertTrue(sql.contains("COUNT(DISTINCT e.id)"));
        assertTrue(sql.contains("e.status = 1"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("s.submitTime >= e2.lastPublishedTime"));
        assertTrue(sql.contains("e2.lastPublishedTime IS NOT NULL"));
        assertTrue(sql.contains("cs.isDelete = 0"));
        assertTrue(sql.contains("e.isDelete = 0"));
        assertFalse(sql.contains("status IN"));
    }

    @Test
    void unreadSql_resolvedUnreadExcludingAutoClose() throws Exception {
        Select select = DisputeMapper.class
                .getMethod("countUnreadTeacherReplies", long.class, String.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());
        assertTrue(sql.contains("d.resolveTime IS NOT NULL"));
        assertTrue(sql.contains("d.withdrawnTime IS NULL"));
        assertTrue(sql.contains("d.replyReadTime IS NULL"));
        assertTrue(sql.contains("#{autoCloseNote}"));
    }

    @Test
    void markReadSql_stampsUnreadResolvedOnExperiment() throws Exception {
        Update update = DisputeMapper.class
                .getMethod("markRepliesRead", long.class, long.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        String sql = String.join("\n", update.value());
        assertTrue(sql.contains("d.replyReadTime = #{now}"));
        assertTrue(sql.contains("d.resolveTime IS NOT NULL"));
        assertTrue(sql.contains("d.withdrawnTime IS NULL"));
        assertTrue(sql.contains("d.replyReadTime IS NULL"));
        assertTrue(sql.contains("eq.experimentId = #{experimentId}"));
        assertTrue(sql.contains("s.studentId = #{studentId}"));
    }
}
