package org.textup.rest

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.web.converters.configuration.DefaultConverterConfiguration
import org.codehaus.groovy.grails.web.converters.Converter
import org.codehaus.groovy.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.codehaus.groovy.grails.web.converters.marshaller.ObjectMarshaller
import org.textup.util.*

@GrailsTypeChecked
class NamedMarshaller {

    ObjectMarshaller marshaller
    int priority = DefaultConverterConfiguration.DEFAULT_PRIORITY
    String name = MarshallerUtils.MARSHALLER_DEFAULT

    Class<? extends Converter> converterClass
    Class clazz
    Closure closure

    NamedMarshaller(Class<? extends Converter> thisConverter, Class thisClass, Closure thisClosure) {
        converterClass = thisConverter // distinguishes beans in initializer service
        clazz = thisClass
        closure = thisClosure
    }

    ObjectMarshaller getMarshaller() {
        if (!marshaller) {
            marshaller = new ClosureObjectMarshaller(clazz, closure)
        }
        marshaller
    }
}
