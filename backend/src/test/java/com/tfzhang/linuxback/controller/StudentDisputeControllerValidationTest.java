package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.service.StudentDisputeService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class StudentDisputeControllerValidationTest {

    private final ExecutableValidator validator =
            Validation.buildDefaultValidatorFactory().getValidator().forExecutables();
    private final StudentDisputeController controller =
            new StudentDisputeController(mock(StudentDisputeService.class));

    @Test
    void pageSize101_isInvalid() throws Exception {
        assertFalse(validate(1, 101).isEmpty());
    }

    @Test
    void pageSize10_isValid() throws Exception {
        assertTrue(validate(1, 10).isEmpty());
    }

    @Test
    void withdrawDisputeIdZero_isInvalid() throws Exception {
        assertFalse(validateWithdraw(0).isEmpty());
    }

    @Test
    void withdrawDisputeIdOne_isValid() throws Exception {
        assertTrue(validateWithdraw(1).isEmpty());
    }

    private Set<ConstraintViolation<StudentDisputeController>> validate(int page, int pageSize)
            throws NoSuchMethodException {
        return validator.validateParameters(
                controller,
                StudentDisputeController.class.getMethod("listDisputes", int.class, int.class),
                new Object[]{page, pageSize});
    }

    private Set<ConstraintViolation<StudentDisputeController>> validateWithdraw(long disputeId)
            throws NoSuchMethodException {
        return validator.validateParameters(
                controller,
                StudentDisputeController.class.getMethod("withdraw", long.class),
                new Object[]{disputeId});
    }
}
