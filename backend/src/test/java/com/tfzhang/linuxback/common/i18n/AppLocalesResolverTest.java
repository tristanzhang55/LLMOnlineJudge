package com.tfzhang.linuxback.common.i18n;

import com.tfzhang.linuxback.config.LocaleConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppLocalesResolverTest {

    private final LocaleResolver resolver = new LocaleConfig().localeResolver();

    @Test
    void missingHeader_isZhCn() {
        assertEquals(AppLocales.DEFAULT, resolver.resolveLocale(new MockHttpServletRequest()));
    }

    @ParameterizedTest
    @CsvSource({
            "'', zh, CN",
            "zh-CN, zh, CN",
            "zh, zh, CN",
            "zh-TW, zh, CN",
            "en-US, en, US",
            "en, en, US",
            "en-GB, en, US",
            "fr-FR, zh, CN",
            "'en-US,zh;q=0.9', en, US",
            "'zh-CN,en;q=0.5', zh, CN",
            "*, zh, CN"
    })
    void acceptLanguage_mapsToSupportedLocale(String header, String language, String country) {
        assertEquals(Locale.of(language, country), LocaleConfig.resolveAcceptLanguage(header), header);
    }

    @Test
    void resolverReadsAcceptLanguageHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
        assertEquals(Locale.US, resolver.resolveLocale(request));
    }
}
