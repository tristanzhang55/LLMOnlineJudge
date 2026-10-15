package com.tfzhang.linuxback.security;

public record JwtPrincipal(long id, String userType, String userRole, int tv) {

    public JwtPrincipal(long id, String userType, String userRole) {
        this(id, userType, userRole, 0);
    }
}
