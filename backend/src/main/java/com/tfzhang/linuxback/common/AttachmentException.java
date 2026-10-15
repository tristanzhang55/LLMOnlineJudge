package com.tfzhang.linuxback.common;

public class AttachmentException extends RuntimeException {

    private final String code;

    public AttachmentException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
