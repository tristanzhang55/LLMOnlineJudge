package com.tfzhang.linuxback.support;

import com.tfzhang.linuxback.security.JwtPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public final class TestAuth {

    private TestAuth() {
    }

    public static void setTeacher(long id) {
        var principal = new JwtPrincipal(id, "teacher", "user");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void setStudent(long id) {
        var principal = new JwtPrincipal(id, "student", "user");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
