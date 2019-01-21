package org.textup.rest

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.web.converters.configuration.DefaultConverterConfiguration
import org.codehaus.groovy.grails.web.converters.Converter
import org.codehaus.groovy.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.codehaus.groovy.grails.web.converters.marshaller.ObjectMarshaller

@GrailsTypeChecked
class NamedMarshaller {

    ObjectMarshaller marshaller
    Class<? extends Converter> converterClass
    int priority = DefaultConverterConfiguration.DEFAULT_PRIORITY
    String name = MarshallerUtils.MARSHALLER_DEFAULT
    Closure closure
    Class clazz

    NamedMarshaller(Class<? extends Converter> converterClass, Class clazz, Closure closure) {
        converterClass = converterClass // distinguishes beans in initializer service
        clazz = clazz
        closure = closure
    }

    ObjectMarshaller getMarshaller() {
        if (!marshaller) {
            marshaller = new ClosureObjectMarshaller(clazz, closure)
        }
        marshaller
    }
}
