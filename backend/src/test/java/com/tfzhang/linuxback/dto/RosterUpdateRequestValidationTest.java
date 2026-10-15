package com.tfzhang.linuxback.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RosterUpdateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankNameOrMajor_isInvalid() {
        RosterUpdateRequest request = valid();
        request.setStudentName("  ");
        assertFalse(validator.validate(request).isEmpty());

        request = valid();
        request.setMajor("");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void classIdMissingOrZero_isInvalid() {
        RosterUpdateRequest missing = valid();
        missing.setClassId(null);
        assertFalse(validator.validate(missing).isEmpty());

        RosterUpdateRequest zero = valid();
        zero.setClassId(0L);
        assertFalse(validator.validate(zero).isEmpty());
    }

    @Test
    void validRequest_isAccepted() {
        assertTrue(validator.validate(valid()).isEmpty());
    }

    private static RosterUpdateRequest valid() {
        RosterUpdateRequest request = new RosterUpdateRequest();
        request.setStudentName("张伟");
        request.setMajor("软件工程");
        request.setClassId(88L);
        return request;
    }
}
