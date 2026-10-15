package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubmissionRoundStatusTest {

    @Test
    void pending_whenAllStatusZero() {
        assertEquals(SubmissionRoundStatus.PENDING, SubmissionRoundStatus.derive(3, 0, 0, 0));
        assertNull(SubmissionRoundStatus.scoreForStatus(SubmissionRoundStatus.PENDING, 10L));
    }

    @Test
    void gradingInProgress_whenAnyStatusOne() {
        assertEquals(
                SubmissionRoundStatus.GRADING_IN_PROGRESS,
                SubmissionRoundStatus.derive(3, 1, 0, 0));
        assertNull(SubmissionRoundStatus.scoreForStatus(SubmissionRoundStatus.GRADING_IN_PROGRESS, 10L));
    }

    @Test
    void completed_whenEveryRowStatusTwo() {
        assertEquals(SubmissionRoundStatus.COMPLETED, SubmissionRoundStatus.derive(3, 0, 3, 0));
        assertEquals(90, SubmissionRoundStatus.scoreForStatus(SubmissionRoundStatus.COMPLETED, 90L));
    }

    @Test
    void gradingFailed_whenAnyStatusThree() {
        assertEquals(SubmissionRoundStatus.GRADING_FAILED, SubmissionRoundStatus.derive(3, 1, 1, 1));
        assertNull(SubmissionRoundStatus.scoreForStatus(SubmissionRoundStatus.GRADING_FAILED, 40L));
    }

    @Test
    void failedTakesPrecedenceOverCompleted() {
        assertEquals(SubmissionRoundStatus.GRADING_FAILED, SubmissionRoundStatus.derive(2, 0, 1, 1));
    }

    @Test
    void deriveFromAggregateRow() {
        SubmissionRoundAggregateRow row = new SubmissionRoundAggregateRow();
        row.setQuestionCount(2L);
        row.setGradingCount(0L);
        row.setDoneCount(2L);
        row.setFailedCount(0L);
        row.setScoreSum(15L);
        assertEquals(SubmissionRoundStatus.COMPLETED, SubmissionRoundStatus.derive(row));
    }
}
