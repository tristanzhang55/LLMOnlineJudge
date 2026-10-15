package com.tfzhang.linuxback.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StudentSubmissionHistoryRoundVO {

    private LocalDateTime submitTime;
    private String roundLabel;
    private List<StudentSubmissionHistoryAnswerVO> answers;
}
