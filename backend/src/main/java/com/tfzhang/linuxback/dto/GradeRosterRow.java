package com.tfzhang.linuxback.dto;

import lombok.Data;

@Data
public class GradeRosterRow {

    private Long classStudentId;
    private Long userId;
    private String studentId;
    private String studentName;
    private String className;
    private String major;
}
