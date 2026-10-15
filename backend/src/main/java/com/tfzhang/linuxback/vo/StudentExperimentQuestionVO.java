package com.tfzhang.linuxback.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class StudentExperimentQuestionVO {

    private Long id;
    private Long experimentId;
    private Long questionId;
    private String questionTitle;
    private String questionContent;
    private Integer effectivePoints;
    private Integer sortOrder;
    private List<StudentSubQuestionVO> subQuestions;
    private JsonNode currentAnswerBlocks;
    private JsonNode prefillAnswerBlocks;
}
