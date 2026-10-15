package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.entity.SubQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "app.grading.scorer", havingValue = "fake", matchIfMissing = true)
@RequiredArgsConstructor
public class FakeSubQuestionGrader implements SubQuestionGrader {

    // i18n: persisted content, do not localize
    static final String PLACEHOLDER_EVIDENCE = "（占位实现，尚未接入真实批改）";

    private final GradingProperties gradingProperties;

    @Override
    public GradeResult grade(SubQuestion subQuestion, JsonNode answerForSubQuestion) {
        String format = subQuestion.getAnswerFormat() == null
                ? ""
                : subQuestion.getAnswerFormat().toLowerCase();
        switch (format) {
            case "text", "code", "image", "" -> {
                // fake body for every branch this pass
            }
            default -> {
                // still fake-grade unknown formats
            }
        }
        sleep();
        int marks = subQuestion.getScore() == null ? 0 : subQuestion.getScore();
        return new GradeResult(marks, PLACEHOLDER_EVIDENCE, 0, PLACEHOLDER_EVIDENCE);
    }

    private void sleep() {
        int min = gradingProperties.getFakeScoreDelayMinMs();
        int max = gradingProperties.getFakeScoreDelayMaxMs();
        if (max <= min) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(min, max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("fake grader interrupted", e);
        }
    }
}
