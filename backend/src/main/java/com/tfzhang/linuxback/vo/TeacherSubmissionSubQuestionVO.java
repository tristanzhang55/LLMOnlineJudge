package com.tfzhang.linuxback.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class TeacherSubmissionSubQuestionVO {

    private Long subQuestionId;
    private String content;
    private String referenceAnswer;
    private String rubric;
    private JsonNode studentAnswerBlocks;
    private Integer maxScore;
    private Integer aiScore;
    private Integer effectiveScore;
    private Boolean usedSandbox;
    private String aiEvidence;
    private String gradingLog;
}
