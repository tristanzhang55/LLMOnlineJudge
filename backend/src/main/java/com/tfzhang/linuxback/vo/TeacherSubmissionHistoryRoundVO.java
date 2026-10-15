package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TeacherSubmissionHistoryRoundVO {

    private LocalDateTime submitTime;
    private List<TeacherSubmissionQuestionVO> questions;
}
