package com.tfzhang.linuxback.common;

import com.tfzhang.linuxback.security.JwtPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static JwtPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal principal)) {
            throw AuthException.invalidToken();
        }
        return principal;
    }

    public static long requireId() {
        return require().id();
    }
}
