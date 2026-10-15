package com.tfzhang.linuxback.common;

import com.tfzhang.linuxback.common.i18n.Msg;

import java.util.List;

public class RosterException extends RuntimeException {

    private final String code;
    private final List<ErrorResponse.FieldError> fieldErrors;

    public RosterException(String message, String code, List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static RosterException invalidFile(String message) {
        return new RosterException(
                message,
                "INVALID_PARAMETER",
                List.of(new ErrorResponse.FieldError("file", message)));
    }

    public static RosterException notFound() {
        return new RosterException(Msg.t("error.roster.notFound"), "NOT_FOUND", null);
    }

    public static RosterException conflict(String message, String field) {
        return new RosterException(
                message,
                "CONFLICT",
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    public static RosterException invalid(String message, String field) {
        return new RosterException(
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
