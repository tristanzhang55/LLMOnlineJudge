package com.tfzhang.linuxback.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;

@Component
@Order(-104)
@RequiredArgsConstructor
public class LocaleContextFilter extends OncePerRequestFilter {

    private final LocaleResolver localeResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        try {
            LocaleContextHolder.setLocale(localeResolver.resolveLocale(request));
            filterChain.doFilter(request, response);
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }
}
