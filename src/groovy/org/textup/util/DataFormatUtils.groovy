package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.converters.*
import grails.util.Holders
import groovy.json.*
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class DataFormatUtils {

    static String toXmlString(Object data) {
        if (data) {
            (data as XML).toString()
        }
        else { "" }
    }

    // [NOTE] use `new JSON()` instead of `as JSON` because the case is only implemented for domain objects
    // see: https://stackoverflow.com/a/30989508
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static String toJsonString(Object data) {
        if (data) {
            JSON.use(MarshallerUtils.MARSHALLER_DEFAULT) {
                new JSON(data).toString()
            }
        }
        else { "" }
    }

    static Object jsonToObject(Object data) throws JsonException {
        data ? new JsonSlurper().parseText(toJsonString(data)) : null
    }

    static Object jsonToObject(String str) throws JsonException {
        str ? new JsonSlurper().parseText(str) : null
    }
}
