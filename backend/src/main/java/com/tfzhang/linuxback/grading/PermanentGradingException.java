package com.tfzhang.linuxback.grading;

public class PermanentGradingException extends RuntimeException {

    public PermanentGradingException(String message) {
        super(message);
    }

    public PermanentGradingException(String message, Throwable cause) {
        super(message, cause);
    }
}
