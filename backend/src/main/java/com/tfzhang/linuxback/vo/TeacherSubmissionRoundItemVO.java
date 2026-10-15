package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TeacherSubmissionRoundItemVO {

    private Long experimentId;
    private String experimentTitle;
    private Long classId;
    private Long studentId;
    private String studentName;
    private LocalDateTime submitTime;
    private Integer questionCount;
    private String status;
    private Integer score;
}
