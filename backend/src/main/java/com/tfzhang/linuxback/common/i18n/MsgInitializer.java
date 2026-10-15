package com.tfzhang.linuxback.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class MsgInitializer {

    public MsgInitializer(MessageSource messageSource) {
        Msg.setMessageSource(messageSource);
    }
}
