package com.tfzhang.linuxback.config;

import com.tfzhang.linuxback.common.i18n.AppLocales;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return resolveAcceptLanguage(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
            }
        };
        resolver.setSupportedLocales(AppLocales.SUPPORTED);
        resolver.setDefaultLocale(AppLocales.DEFAULT);
        return resolver;
    }

    public static Locale resolveAcceptLanguage(String header) {
        if (header == null || header.isBlank() || "*".equals(header.trim())) {
            return AppLocales.DEFAULT;
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                String tag = range.getRange();
                if ("*".equals(tag)) {
                    continue;
                }
                Locale requested = Locale.forLanguageTag(tag);
                for (Locale supported : AppLocales.SUPPORTED) {
                    if (supported.equals(requested)) {
                        return supported;
                    }
                }
                for (Locale supported : AppLocales.SUPPORTED) {
                    if (supported.getLanguage().equalsIgnoreCase(requested.getLanguage())) {
                        return supported;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // garbage header → default
        }
        return AppLocales.DEFAULT;
    }
}
