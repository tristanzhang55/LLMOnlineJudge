package com.tfzhang.linuxback.security;

import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> TEACHER_PREFIXES = List.of(
            "/questions", "/experiments", "/classes", "/teacher");
    private static final List<String> STUDENT_PREFIXES = List.of("/student", "/submissions");
    private static final Pattern STUDENT_SUBMIT =
            Pattern.compile("^/experiments/[1-9][0-9]*/submit$");
    private static final Pattern STUDENT_STATE =
            Pattern.compile("^/experiments/[1-9][0-9]*/my-submission-state$");

    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublicRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtPrincipal principal;
        try {
            principal = jwtService.parse(token);
        } catch (AuthException ex) {
            SecurityJsonResponse.writeUnauthorized(response);
            return;
        }

        if (!acceptLiveUser(principal)) {
            SecurityJsonResponse.writeUnauthorized(response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String method = request.getMethod();
        String path = request.getServletPath();
        if (isTeacherOnlyPath(method, path) && !"teacher".equals(principal.userType())) {
            SecurityJsonResponse.writeForbidden(response);
            return;
        }

        if (isStudentOnlyPath(method, path) && !"student".equals(principal.userType())) {
            SecurityJsonResponse.writeForbidden(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    boolean acceptLiveUser(JwtPrincipal principal) {
        User user = userMapper.selectById(principal.id());
        if (user == null || "ban".equals(user.getUserRole())) {
            return false;
        }
        if (!Objects.equals(user.getUserType(), principal.userType())) {
            return false;
        }
        int rowTv = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return rowTv == principal.tv();
    }

    static boolean isPublicRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if ("OPTIONS".equals(method)) {
            return true;
        }
        if ("POST".equals(method) && "/auth/login".equals(path)) {
            return true;
        }
        return "GET".equals(method) && path.startsWith("/files/");
    }

    static boolean isStudentExperimentException(String method, String path) {
        if ("POST".equals(method) && STUDENT_SUBMIT.matcher(path).matches()) {
            return true;
        }
        return "GET".equals(method) && STUDENT_STATE.matcher(path).matches();
    }

    static boolean isTeacherOnlyPath(String method, String path) {
        if (isStudentExperimentException(method, path)) {
            return false;
        }
        return TEACHER_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    static boolean isStudentOnlyPath(String method, String path) {
        if (isStudentExperimentException(method, path)) {
            return true;
        }
        return STUDENT_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    static boolean isTeacherOnlyPath(String path) {
        return isTeacherOnlyPath("GET", path);
    }

    static boolean isStudentOnlyPath(String path) {
        return isStudentOnlyPath("GET", path);
    }
}
