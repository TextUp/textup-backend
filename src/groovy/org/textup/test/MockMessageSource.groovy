package org.textup.test

import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable

class MockMessageSource implements MessageSource {

    String getMessage(MessageSourceResolvable resolvable, Locale locale) {
        resolvable.codes.last()
    }

    String getMessage(String code, Object[] args, Locale locale) {
        code
    }

    String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        code
    }
}
