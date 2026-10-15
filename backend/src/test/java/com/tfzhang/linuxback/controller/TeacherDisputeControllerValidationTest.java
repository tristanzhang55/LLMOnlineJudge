package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.dto.DisputeResolveRequest;
import com.tfzhang.linuxback.service.TeacherDisputeService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TeacherDisputeControllerValidationTest {

    private final ExecutableValidator validator =
            Validation.buildDefaultValidatorFactory().getValidator().forExecutables();
    private final TeacherDisputeController controller =
            new TeacherDisputeController(mock(TeacherDisputeService.class));

    @Test
    void resolveDisputeIdZero_isInvalid() throws Exception {
        assertFalse(validateResolve(0).isEmpty());
    }

    @Test
    void resolveDisputeIdOne_isValid() throws Exception {
        assertTrue(validateResolve(1).isEmpty());
    }

    private Set<ConstraintViolation<TeacherDisputeController>> validateResolve(long disputeId)
            throws NoSuchMethodException {
        DisputeResolveRequest body = new DisputeResolveRequest();
        body.setManualComment("ok");
        return validator.validateParameters(
                controller,
                TeacherDisputeController.class.getMethod(
                        "resolve", long.class, DisputeResolveRequest.class),
                new Object[]{disputeId, body});
    }
}
