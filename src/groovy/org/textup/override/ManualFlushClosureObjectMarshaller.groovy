package org.textup.override

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.web.converters.Converter
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
import org.codehaus.groovy.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.textup.util.*

// Override closure marshaller to not automatically flush the session when marshalling to avoid any
// StaleObjectExceptions if a static finder triggers a session flush
// See: https://github.com/grails/grails-core/blob/v2.4.4/grails-plugin-converters/src/main/groovy/org/codehaus/groovy/grails/web/converters/marshaller/ClosureObjectMarshaller.java

@GrailsTypeChecked
class ManualFlushClosureObjectMarshaller<T extends Converter> extends ClosureObjectMarshaller<T> {

    ManualFlushClosureObjectMarshaller(Class<?> clazz, Closure closure) {
        super(clazz, closure)
    }

    @Override
    void marshalObject(Object object, T converter) throws ConverterException {
        Utils.doWithoutFlush { super.marshalObject(object, converter) }
    }
}
