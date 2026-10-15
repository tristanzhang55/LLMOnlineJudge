package com.tfzhang.linuxback.common.i18n;

import java.util.List;
import java.util.Locale;

public final class AppLocales {

    public static final Locale DEFAULT = Locale.SIMPLIFIED_CHINESE;
    public static final List<Locale> SUPPORTED = List.of(Locale.SIMPLIFIED_CHINESE, Locale.US);

    private AppLocales() {
    }
}
