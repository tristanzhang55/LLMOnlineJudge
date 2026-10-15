package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.entity.SubQuestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSubQuestionGraderTest {

    @Test
    void grade_awardsFullMarksWithPlaceholderEvidence() {
        GradingProperties properties = new GradingProperties();
        properties.setFakeScoreDelayMinMs(0);
        properties.setFakeScoreDelayMaxMs(0);
        FakeSubQuestionGrader grader = new FakeSubQuestionGrader(properties);
        SubQuestion sq = new SubQuestion();
        sq.setScore(7);
        sq.setAnswerFormat("code");

        SubQuestionGrader.GradeResult result = grader.grade(sq, JsonNodeFactory.instance.objectNode());

        assertEquals(7, result.aiScore());
        assertEquals(0, result.usedSandbox());
        assertEquals(FakeSubQuestionGrader.PLACEHOLDER_EVIDENCE, result.aiEvidence());
        assertTrue(result.gradingLog().contains("占位"));
    }
}
