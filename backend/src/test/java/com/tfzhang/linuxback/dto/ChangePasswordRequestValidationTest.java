package com.tfzhang.linuxback.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePasswordRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankOldPassword_isInvalid() {
        ChangePasswordRequest request = valid();
        request.setOldPassword("  ");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void newPassword_7charsInvalid_8charsValid_73charsInvalid() {
        ChangePasswordRequest seven = valid();
        seven.setNewPassword("abcdefg");
        assertFalse(validator.validate(seven).isEmpty());

        ChangePasswordRequest eight = valid();
        eight.setNewPassword("abcdefgh");
        assertTrue(validator.validate(eight).isEmpty());

        ChangePasswordRequest seventyThree = valid();
        seventyThree.setNewPassword("a".repeat(73));
        assertFalse(validator.validate(seventyThree).isEmpty());
    }

    @Test
    void oldPassword_6charsIsAccepted() {
        ChangePasswordRequest request = valid();
        request.setOldPassword("123456");
        assertTrue(validator.validate(request).isEmpty());
    }

    private static ChangePasswordRequest valid() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldpass1");
        request.setNewPassword("abcdefgh");
        return request;
    }
}
