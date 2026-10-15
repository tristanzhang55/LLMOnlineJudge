package com.tfzhang.linuxback.common;

import com.tfzhang.linuxback.common.i18n.Msg;

import java.util.List;

public class ExperimentException extends RuntimeException {

    private final String code;
    private final List<ErrorResponse.FieldError> fieldErrors;

    public ExperimentException(String message, String code, List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static ExperimentException notFound() {
        return new ExperimentException(Msg.t("error.experiment.notFound"), "NOT_FOUND", null);
    }

    public static ExperimentException validationFailed(List<ErrorResponse.FieldError> fieldErrors) {
        return new ExperimentException(Msg.t("error.validationFailed"), "INVALID_PARAMETER", fieldErrors);
    }

    public static ExperimentException conflict(String message, List<ErrorResponse.FieldError> fieldErrors) {
        return new ExperimentException(message, "CONFLICT", fieldErrors);
    }

    public static ExperimentException questionRemovalBlocked() {
        String message = Msg.t("error.experiment.questionRemovalBlocked");
        return conflict(message, List.of(new ErrorResponse.FieldError("questions", message)));
    }

    public static ExperimentException alreadySubmitted() {
        String message = Msg.t("error.experiment.alreadySubmitted");
        return conflict(message, List.of(new ErrorResponse.FieldError("answers", message)));
    }

    public static ExperimentException forbidden(String message) {
        return new ExperimentException(message, "FORBIDDEN", null);
    }

    public static ExperimentException draftLocked() {
        return forbidden(Msg.t("error.experiment.draftLocked"));
    }

    public static ExperimentException draftNotPublished() {
        String message = Msg.t("error.experiment.draftNotPublished");
        return conflict(message, List.of(new ErrorResponse.FieldError("status", message)));
    }

    public static ExperimentException notOpenForSubmission() {
        String message = Msg.t("error.experiment.notOpenForSubmission");
        return conflict(message, List.of(new ErrorResponse.FieldError("status", message)));
    }

    public static ExperimentException disputeNotGraded() {
        String message = Msg.t("error.experiment.disputeNotGraded");
        return new ExperimentException(
                message,
                "INVALID_PARAMETER",
                List.of(new ErrorResponse.FieldError("status", message)));
    }

    public static ExperimentException disputeAlreadyOpen() {
        String message = Msg.t("error.experiment.disputeAlreadyOpen");
        return conflict(message, List.of(new ErrorResponse.FieldError("disputeStatus", message)));
    }

    public static ExperimentException disputeAlreadyResolved() {
        String message = Msg.t("error.experiment.disputeAlreadyResolved");
        return conflict(message, List.of(new ErrorResponse.FieldError("disputeStatus", message)));
    }

    public static ExperimentException disputeAlreadyHandled() {
        String message = Msg.t("error.experiment.disputeAlreadyHandled");
        return conflict(message, List.of(new ErrorResponse.FieldError("disputeStatus", message)));
    }

    public static ExperimentException disputeAlreadyWithdrawn() {
        String message = Msg.t("error.experiment.disputeAlreadyWithdrawn");
        return conflict(message, List.of(new ErrorResponse.FieldError("disputeStatus", message)));
    }

    public static ExperimentException resolveSubQuestionNotOnQuestion(long subQuestionId) {
        String message = Msg.t("error.experiment.subQuestionNotOnQuestion", subQuestionId);
        return validationFailed(List.of(new ErrorResponse.FieldError("subQuestionId", message)));
    }

    public static ExperimentException resolveManualScoreOutOfRange(long subQuestionId) {
        String message = Msg.t("error.experiment.manualScoreOutOfRange", subQuestionId);
        return validationFailed(List.of(new ErrorResponse.FieldError("manualScore", message)));
    }

    public static ExperimentException invalidDisputeStatusFilter() {
        return validationFailed(
                List.of(new ErrorResponse.FieldError("disputeStatus", Msg.t("error.experiment.invalidDisputeStatus"))));
    }

    public String getCode() {
        return code;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
