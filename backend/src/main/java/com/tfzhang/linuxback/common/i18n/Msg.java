package com.tfzhang.linuxback.common.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

public final class Msg {

    private static final Logger log = LoggerFactory.getLogger(Msg.class);

    private static volatile MessageSource messageSource;

    private Msg() {
    }

    static void setMessageSource(MessageSource source) {
        messageSource = source;
    }

    public static String t(String key, Object... args) {
        Locale locale = currentLocale();
        MessageSource source = source();
        Object[] messageArgs = args == null || args.length == 0 ? null : args;
        try {
            return source.getMessage(key, messageArgs, locale);
        } catch (NoSuchMessageException ex) {
            log.error("i18n key missing: {} ({})", key, locale);
            if (!AppLocales.DEFAULT.equals(locale)) {
                try {
                    return source.getMessage(key, messageArgs, AppLocales.DEFAULT);
                } catch (NoSuchMessageException ignored) {
                    // fall through to the key
                }
            }
            return key;
        }
    }

    private static Locale currentLocale() {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        if (context == null || context.getLocale() == null) {
            return AppLocales.DEFAULT;
        }
        return context.getLocale();
    }

    private static MessageSource source() {
        MessageSource current = messageSource;
        if (current == null) {
            synchronized (Msg.class) {
                current = messageSource;
                if (current == null) {
                    current = fallbackSource();
                    messageSource = current;
                }
            }
        }
        return current;
    }

    private static ResourceBundleMessageSource fallbackSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }
}
