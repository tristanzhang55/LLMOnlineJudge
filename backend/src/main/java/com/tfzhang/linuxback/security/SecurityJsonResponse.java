package com.tfzhang.linuxback.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfzhang.linuxback.common.ErrorResponse;
import com.tfzhang.linuxback.common.i18n.Msg;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class SecurityJsonResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SecurityJsonResponse() {
    }

    public static void writeUnauthorized(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, Msg.t("error.auth.unauthenticated"), "UNAUTHORIZED");
    }

    public static void writeForbidden(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, Msg.t("error.auth.forbidden"), "FORBIDDEN");
    }

    private static void write(HttpServletResponse response, int status, String message, String code)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(), new ErrorResponse(message, code));
    }
}
