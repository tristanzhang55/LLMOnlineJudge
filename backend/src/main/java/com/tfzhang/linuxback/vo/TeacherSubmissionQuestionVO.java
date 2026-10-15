package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TeacherSubmissionQuestionVO {

    private Long experimentQuestionId;
    private Long submissionId;
    private Integer status;
    private Integer score;
    private Integer disputeStatus;
    private String disputeReason;
    private LocalDateTime disputeTime;
    private String manualComment;
    private LocalDateTime disputeResolveTime;
    private List<TeacherDisputeHistoryItemVO> disputes;
    private List<TeacherSubmissionSubQuestionVO> subQuestions;
}
