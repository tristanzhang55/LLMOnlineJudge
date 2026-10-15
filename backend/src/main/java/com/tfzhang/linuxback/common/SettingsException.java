package com.tfzhang.linuxback.common;

import java.util.List;

public class SettingsException extends RuntimeException {

    private final String code;
    private final List<ErrorResponse.FieldError> fieldErrors;

    public SettingsException(String message, String code, List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static SettingsException invalid(String message, String field) {
        return new SettingsException(
                message,
                "INVALID_PARAMETER",
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    public String getCode() {
        return code;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
