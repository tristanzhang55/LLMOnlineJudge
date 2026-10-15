package com.tfzhang.linuxback.common;

import com.tfzhang.linuxback.common.i18n.Msg;

import java.util.List;

public class GradeException extends RuntimeException {

    private final String code;
    private final List<ErrorResponse.FieldError> fieldErrors;

    public GradeException(String message, String code, List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static GradeException notFound() {
        return new GradeException(Msg.t("error.grade.notFound"), "NOT_FOUND", null);
    }

    public static GradeException invalid(String message, String field) {
        return new GradeException(
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
