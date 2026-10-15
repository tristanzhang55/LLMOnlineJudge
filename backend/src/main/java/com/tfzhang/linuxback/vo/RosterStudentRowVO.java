package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RosterStudentRowVO {

    private Long classStudentId;
    private String studentId;
    private String studentName;
    private String major;
    private Long classId;
    private String className;
    private String semester;
    private LocalDateTime joinTime;
}
