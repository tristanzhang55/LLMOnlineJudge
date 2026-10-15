package com.tfzhang.linuxback.common;

import com.tfzhang.linuxback.common.i18n.Msg;

public class AuthException extends RuntimeException {

    private final String code;

    public AuthException(String message, String code) {
        super(message);
        this.code = code;
    }

    public static AuthException badCredentials() {
        return new AuthException(Msg.t("error.auth.badCredentials"), "UNAUTHORIZED");
    }

    public static AuthException invalidToken() {
        return new AuthException(Msg.t("error.auth.unauthenticated"), "UNAUTHORIZED");
    }

    public static AuthException forbidden() {
        return new AuthException(Msg.t("error.auth.forbidden"), "FORBIDDEN");
    }

    public static AuthException tooManyRequests() {
        return new AuthException(Msg.t("error.auth.tooManyRequests"), "TOO_MANY_REQUESTS");
    }

    public String getCode() {
        return code;
    }
}
