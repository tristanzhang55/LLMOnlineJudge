package com.tfzhang.linuxback.vo;

import lombok.Data;

@Data
public class GradeStudentRowVO {

    private String studentId;
    private String studentName;
    private String className;
    private Integer score;
    private String status;
}
