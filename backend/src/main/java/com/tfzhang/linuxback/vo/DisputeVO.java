package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DisputeVO {

    private Long id;
    private Long submissionId;
    private Integer disputeStatus;
    private String disputeReason;
    private LocalDateTime disputeTime;
    private String manualComment;
    private LocalDateTime resolveTime;
    private LocalDateTime withdrawnTime;
}
