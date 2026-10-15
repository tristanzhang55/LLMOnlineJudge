package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;

public final class SubmissionRoundStatus {

    public static final String PENDING = "pending";
    public static final String GRADING_IN_PROGRESS = "grading_in_progress";
    public static final String COMPLETED = "completed";
    public static final String GRADING_FAILED = "grading_failed";

    private SubmissionRoundStatus() {
    }

    public static String derive(long questionCount, long gradingCount, long doneCount, long failedCount) {
        if (failedCount > 0) {
            return GRADING_FAILED;
        }
        if (questionCount > 0 && doneCount == questionCount) {
            return COMPLETED;
        }
        if (gradingCount > 0) {
            return GRADING_IN_PROGRESS;
        }
        return PENDING;
    }

    public static Integer scoreForStatus(String status, Long scoreSum) {
        if (!COMPLETED.equals(status) || scoreSum == null) {
            return null;
        }
        return scoreSum.intValue();
    }

    public static String derive(SubmissionRoundAggregateRow row) {
        long questionCount = nz(row.getQuestionCount());
        return derive(questionCount, nz(row.getGradingCount()), nz(row.getDoneCount()), nz(row.getFailedCount()));
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
