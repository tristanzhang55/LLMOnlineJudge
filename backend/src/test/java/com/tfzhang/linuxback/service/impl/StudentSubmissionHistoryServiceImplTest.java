package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.StudentSubmissionHistoryItemVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSubmissionHistoryServiceImplTest {

    private static final long STUDENT_ID = 2L;

    @Mock
    private SubmissionMapper submissionMapper;

    private StudentSubmissionHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        TestAuth.setStudent(STUDENT_ID);
        service = new StudentSubmissionHistoryServiceImpl(submissionMapper);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void listRounds_empty_skipsPageQuery() {
        when(submissionMapper.countSubmissionRounds(STUDENT_ID)).thenReturn(0L);

        PageResult<StudentSubmissionHistoryItemVO> result = service.listRounds(1, 10);

        assertEquals(0, result.getTotal());
        assertEquals(List.of(), result.getData());
        verify(submissionMapper, never()).findSubmissionRounds(STUDENT_ID, 10, 0);
    }

    @Test
    void listRounds_mapsPendingAndUsesOffset() {
        when(submissionMapper.countSubmissionRounds(STUDENT_ID)).thenReturn(12L);
        SubmissionRoundAggregateRow row = new SubmissionRoundAggregateRow();
        row.setExperimentId(1L);
        row.setExperimentTitle("Linux 基础实验一");
        row.setSubmitTime(LocalDateTime.of(2026, 8, 26, 13, 39, 21));
        row.setQuestionCount(3L);
        row.setPendingOrGradingCount(3L);
        row.setGradingCount(0L);
        row.setDoneCount(0L);
        row.setFailedCount(0L);
        row.setScoreSum(null);
        when(submissionMapper.findSubmissionRounds(STUDENT_ID, 10, 10)).thenReturn(List.of(row));

        PageResult<StudentSubmissionHistoryItemVO> result = service.listRounds(2, 10);

        assertEquals(12, result.getTotal());
        assertEquals(1, result.getData().size());
        StudentSubmissionHistoryItemVO item = result.getData().get(0);
        assertEquals(1L, item.getExperimentId());
        assertEquals("pending", item.getStatus());
        assertEquals(3, item.getQuestionCount());
        assertNull(item.getScore());
    }
}
