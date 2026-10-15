package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherDisputeInboxItemVO {

    private Long id;
    private Long submissionId;
    private Long experimentId;
    private String experimentTitle;
    private Long classId;
    private Long studentId;
    private String studentName;
    private Long experimentQuestionId;
    private String questionTitle;
    private Integer submissionStatus;
    private Integer score;
    private Integer disputeStatus;
    private String disputeReason;
    private LocalDateTime disputeTime;
    private String manualComment;
    private LocalDateTime resolveTime;
    private Integer isManualOverride;
}
