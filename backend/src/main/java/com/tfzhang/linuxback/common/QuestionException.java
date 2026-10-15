package com.tfzhang.linuxback.common;

import com.tfzhang.linuxback.common.i18n.Msg;

import java.util.List;

public class QuestionException extends RuntimeException {

    private final String code;
    private final List<ErrorResponse.FieldError> fieldErrors;

    public QuestionException(String message, String code, List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static QuestionException notFound() {
        return new QuestionException(Msg.t("error.question.notFound"), "NOT_FOUND", null);
    }

    public static QuestionException validationFailed(List<ErrorResponse.FieldError> fieldErrors) {
        return new QuestionException(Msg.t("error.validationFailed"), "INVALID_PARAMETER", fieldErrors);
    }

    public String getCode() {
        return code;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
