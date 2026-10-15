package com.tfzhang.linuxback.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RosterConfirmRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyRows_isInvalid() {
        RosterConfirmRequest request = new RosterConfirmRequest();
        request.setRows(List.of());
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void missingRows_isInvalid() {
        RosterConfirmRequest request = new RosterConfirmRequest();
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rowIndexZero_isInvalid() {
        RosterRowRequest row = new RosterRowRequest();
        row.setRowIndex(0);
        RosterConfirmRequest request = new RosterConfirmRequest();
        request.setRows(List.of(row));
        Set<ConstraintViolation<RosterConfirmRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void validRowIndex_isAccepted() {
        RosterRowRequest row = new RosterRowRequest();
        row.setRowIndex(2);
        RosterConfirmRequest request = new RosterConfirmRequest();
        request.setRows(List.of(row));
        assertTrue(validator.validate(request).isEmpty());
    }
}
