package com.tfzhang.linuxback.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisputeResolveRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankComment_isInvalid() {
        DisputeResolveRequest request = new DisputeResolveRequest();
        request.setManualComment("   ");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void missingComment_isInvalid() {
        DisputeResolveRequest request = new DisputeResolveRequest();
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void trimmedComment_commentOnly_isValid() {
        DisputeResolveRequest request = new DisputeResolveRequest();
        request.setManualComment("  维持原判  ");
        assertTrue(validator.validate(request).isEmpty());
        assertEquals("维持原判", request.getManualComment());
        assertTrue(request.getSubQuestionScores().isEmpty());
    }

    @Test
    void missingSubQuestionId_isInvalid() {
        DisputeResolveRequest request = new DisputeResolveRequest();
        request.setManualComment("ok");
        DisputeResolveScoreItem item = new DisputeResolveScoreItem();
        item.setManualScore(BigDecimal.ONE);
        request.setSubQuestionScores(List.of(item));
        assertFalse(validator.validate(request).isEmpty());
    }
}

class DisputeForwardRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankNote_isInvalid() {
        DisputeForwardRequest request = new DisputeForwardRequest();
        request.setNote("  ");
        request.setDisputeIds(List.of(1L));
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void emptyIds_isInvalid() {
        DisputeForwardRequest request = new DisputeForwardRequest();
        request.setNote("ok");
        request.setDisputeIds(List.of());
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void fiftyOneIds_isInvalid() {
        DisputeForwardRequest request = new DisputeForwardRequest();
        request.setNote("ok");
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 51; i++) {
            ids.add(i);
        }
        request.setDisputeIds(ids);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void zeroId_isInvalid() {
        DisputeForwardRequest request = new DisputeForwardRequest();
        request.setNote("ok");
        request.setDisputeIds(List.of(0L));
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void trimmedNote_oneId_isValid() {
        DisputeForwardRequest request = new DisputeForwardRequest();
        request.setNote("  rubric fixed  ");
        request.setDisputeIds(List.of(501L));
        assertTrue(validator.validate(request).isEmpty());
        assertEquals("rubric fixed", request.getNote());
    }
}
