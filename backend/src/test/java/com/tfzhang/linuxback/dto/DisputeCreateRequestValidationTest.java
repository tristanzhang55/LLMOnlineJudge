package com.tfzhang.linuxback.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisputeCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankReason_isInvalid() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setDisputeReason("   ");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void missingReason_isInvalid() {
        DisputeCreateRequest request = new DisputeCreateRequest();

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void tooLongReason_isInvalid() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setDisputeReason("a".repeat(1025));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void trimmedReason_isValid() {
        DisputeCreateRequest request = new DisputeCreateRequest();
        request.setDisputeReason("  第二小题沙箱输出判错  ");

        assertTrue(validator.validate(request).isEmpty());
        assertEquals("第二小题沙箱输出判错", request.getDisputeReason());
    }
}
