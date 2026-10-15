package com.tfzhang.linuxback.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class MySubmissionQuestionStateVO {

    private Long experimentQuestionId;
    private Long submissionId;
    private JsonNode answerBlocks;
    private String source;
}
