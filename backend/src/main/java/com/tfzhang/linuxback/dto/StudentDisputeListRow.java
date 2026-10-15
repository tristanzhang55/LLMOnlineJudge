package com.tfzhang.linuxback.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentDisputeListRow {

    private Long id;
    private Long submissionId;
    private Long experimentId;
    private String experimentTitle;
    private Long experimentQuestionId;
    private String questionTitle;
    private Integer disputeStatus;
    private LocalDateTime disputeTime;
    private Integer submissionStatus;
    private Integer score;
}
