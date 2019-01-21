package org.textup.util

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class MarshallerUtils {

    // // TODO
    // static final String FALLBACK_SINGULAR = "result"
    // static final String FALLBACK_PLURAL = "results"
    // static final String FALLBACK_RESOURCE_NAME = "resource"



    static final String MARSHALLER_DEFAULT = "default"

    static final String FALLBACK_SINGULAR = "resource"
    static final String FALLBACK_PLURAL = "resources"

    static final String KEY_ANNOUNCEMENT = "announcement"
    KEY_CONTACT

    static String resolveCodeToSingular(String key) {
        String code = "textup.rest.marshallers.${key}.singular"
        Holders.flatConfig[code] ?: MarshallerUtils.FALLBACK_SINGULAR
    }

    static String resolveCodeToPlural(String key) {
        String code = "textup.rest.marshallers.${key}.plural"
        Holders.flatConfig[code] ?: MarshallerUtils.FALLBACK_PLURAL
    }
}
