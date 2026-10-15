package com.tfzhang.linuxback.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentSubmitRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyAnswers_isInvalid() {
        StudentSubmitRequest request = new StudentSubmitRequest();
        request.setAnswers(List.of());

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void nonEmptyAnswers_isValid() {
        StudentSubmitRequest request = new StudentSubmitRequest();
        StudentSubmitAnswerRequest answer = new StudentSubmitAnswerRequest();
        answer.setExperimentQuestionId(41L);
        request.setAnswers(List.of(answer));

        assertTrue(validator.validate(request).isEmpty());
    }
}
