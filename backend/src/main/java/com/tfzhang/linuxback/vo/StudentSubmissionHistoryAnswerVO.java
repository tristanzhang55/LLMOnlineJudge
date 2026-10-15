package com.tfzhang.linuxback.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentSubmissionHistoryAnswerVO {

    private Long experimentQuestionId;
    private LocalDateTime submitTime;
    private JsonNode answerBlocks;
}
