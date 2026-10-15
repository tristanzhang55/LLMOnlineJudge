package com.tfzhang.linuxback.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubmissionRoundAggregateRow {

    private Long experimentId;
    private String experimentTitle;
    private Long classId;
    private Long studentId;
    private String studentName;
    private LocalDateTime submitTime;
    private Long questionCount;
    private Long pendingOrGradingCount;
    private Long gradingCount;
    private Long doneCount;
    private Long failedCount;
    private Long scoreSum;
}
