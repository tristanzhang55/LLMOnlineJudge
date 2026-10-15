package com.tfzhang.linuxback.common.i18n;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MsgTest {

    private Locale previousDefault;

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        if (previousDefault != null) {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void noContext_usesZhCnEvenWhenJvmDefaultIsUs() {
        previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.US);
        LocaleContextHolder.resetLocaleContext();

        assertEquals("账号或密码错误", Msg.t("error.auth.badCredentials"));
    }

    @Test
    void missingKey_logsAndFallsBackToKeyWithoutThrowing() {
        previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.US);
        LocaleContextHolder.setLocale(Locale.US);

        Logger logger = (Logger) LoggerFactory.getLogger(Msg.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertEquals("this.key.does.not.exist", Msg.t("this.key.does.not.exist"));
            assertTrue(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("i18n key missing: this.key.does.not.exist")));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
