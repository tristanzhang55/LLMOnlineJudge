package com.tfzhang.linuxback.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerAuthTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void tooManyRequests_is429() {
        ResponseEntity<ErrorResponse> response = handler.handleAuth(AuthException.tooManyRequests());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("TOO_MANY_REQUESTS", response.getBody().getCode());
        assertEquals("登录失败次数过多，请稍后再试", response.getBody().getMessage());
        assertNull(response.getBody().getFieldErrors());
    }

    @Test
    void unauthorized_is401() {
        ResponseEntity<ErrorResponse> response = handler.handleAuth(AuthException.badCredentials());
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().getCode());
    }

    @Test
    void forbidden_is403() {
        ResponseEntity<ErrorResponse> response = handler.handleAuth(AuthException.forbidden());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
