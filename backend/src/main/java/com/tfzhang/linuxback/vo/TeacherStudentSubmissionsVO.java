package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.List;

@Data
public class TeacherStudentSubmissionsVO {

    private Long experimentId;
    private Long studentId;
    private List<TeacherSubmissionHistoryRoundVO> rounds;
}
