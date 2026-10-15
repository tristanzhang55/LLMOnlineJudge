package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherDisputeHistoryItemVO {

    private Long id;
    private String disputeReason;
    private LocalDateTime disputeTime;
    private String manualComment;
    private LocalDateTime resolveTime;
    private LocalDateTime withdrawnTime;
}
