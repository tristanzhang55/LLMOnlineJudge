package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentSubmissionHistoryItemVO {

    private Long experimentId;
    private String experimentTitle;
    private LocalDateTime submitTime;
    private Integer questionCount;
    private String status;
    private Integer score;
}
