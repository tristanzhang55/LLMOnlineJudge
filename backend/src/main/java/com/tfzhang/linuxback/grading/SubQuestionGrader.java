package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.tfzhang.linuxback.entity.SubQuestion;

public interface SubQuestionGrader {

    GradeResult grade(SubQuestion subQuestion, JsonNode answerForSubQuestion);

    record GradeResult(int aiScore, String aiEvidence, int usedSandbox, String gradingLog) {
    }
}
