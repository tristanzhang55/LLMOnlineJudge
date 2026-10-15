package com.tfzhang.linuxback.common;

import com.tfzhang.linuxback.common.i18n.Msg;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBodyValidation(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return new ErrorResponse(Msg.t("error.validationFailed"), "INVALID_PARAMETER", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException ex) {
        return new ErrorResponse(Msg.t("error.malformedBody"), "INVALID_PARAMETER");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(Msg.t("error.invalidParameter"));
        return new ErrorResponse(message, "INVALID_PARAMETER");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse(Msg.t("error.invalidParameterNamed", ex.getName()), "INVALID_PARAMETER");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return new ErrorResponse(Msg.t("error.fileTooLarge"), "FILE_TOO_LARGE");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingPart(MissingServletRequestPartException ex) {
        return new ErrorResponse(Msg.t("error.fileRequired"), "INVALID_PARAMETER");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParam(MissingServletRequestParameterException ex) {
        if ("ownerType".equals(ex.getParameterName())) {
            return new ErrorResponse(Msg.t("error.invalidOwnerType"), "INVALID_PARAMETER");
        }
        return new ErrorResponse(Msg.t("error.invalidParameterNamed", ex.getParameterName()), "INVALID_PARAMETER");
    }

    @ExceptionHandler(QuestionException.class)
    public ResponseEntity<ErrorResponse> handleQuestion(QuestionException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_PARAMETER" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getMessage(), ex.getCode(), ex.getFieldErrors()));
    }

    @ExceptionHandler(ExperimentException.class)
    public ResponseEntity<ErrorResponse> handleExperiment(ExperimentException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_PARAMETER" -> HttpStatus.BAD_REQUEST;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getMessage(), ex.getCode(), ex.getFieldErrors()));
    }

    @ExceptionHandler(RosterException.class)
    public ResponseEntity<ErrorResponse> handleRoster(RosterException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_PARAMETER" -> HttpStatus.BAD_REQUEST;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getMessage(), ex.getCode(), ex.getFieldErrors()));
    }

    @ExceptionHandler(GradeException.class)
    public ResponseEntity<ErrorResponse> handleGrade(GradeException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_PARAMETER" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getMessage(), ex.getCode(), ex.getFieldErrors()));
    }

    @ExceptionHandler(SettingsException.class)
    public ResponseEntity<ErrorResponse> handleSettings(SettingsException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "INVALID_PARAMETER" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ex.getMessage(), ex.getCode(), ex.getFieldErrors()));
    }

    @ExceptionHandler(AttachmentException.class)
    public ResponseEntity<ErrorResponse> handleAttachment(AttachmentException ex) {
        HttpStatus status = "STORAGE_ERROR".equals(ex.getCode())
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorResponse(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "TOO_MANY_REQUESTS" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No handler or static resource for {}", ex.getResourcePath());
        return new ErrorResponse(Msg.t("error.notFound"), "NOT_FOUND");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ErrorResponse(Msg.t("error.internal"), "INTERNAL_ERROR");
    }
}
