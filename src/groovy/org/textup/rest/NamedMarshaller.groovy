package org.textup.rest

import org.textup.AuthService
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.converters.configuration.DefaultConverterConfiguration
import org.codehaus.groovy.grails.web.converters.Converter
import org.codehaus.groovy.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.codehaus.groovy.grails.web.converters.marshaller.ObjectMarshaller
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.beans.factory.annotation.Autowired

class NamedMarshaller {

    @Autowired
    LinkGenerator linkGenerator
    @Autowired
    SpringSecurityService springSecurityService
    @Autowired
    AuthService authService

    ObjectMarshaller marshaller
    Class<? extends Converter> converterClass
    int priority = DefaultConverterConfiguration.DEFAULT_PRIORITY
    String name
    String namespace
    Closure closure
    Class clazz

    NamedMarshaller(Class<? extends Converter> converterClass, Class clazz, Closure closure) {
        this.converterClass = converterClass //distinguishes beans in initializer service
        this.clazz = clazz
        this.closure = closure
    }

    ObjectMarshaller getMarshaller() {
        if (!this.marshaller) {
            int numParams = this.closure.getMaximumNumberOfParameters()
            if (numParams == 5) {
                this.marshaller = new ClosureObjectMarshaller(this.clazz, this.closure.curry(namespace, springSecurityService, authService, linkGenerator))
            }
            else if (numParams == 4) {
                this.marshaller = new ClosureObjectMarshaller(this.clazz, this.closure.curry(namespace, springSecurityService, linkGenerator))
            }
            else if (numParams == 3) {
                this.marshaller = new ClosureObjectMarshaller(this.clazz, this.closure.curry(namespace, linkGenerator))
            }
            else if (numParams == 2) {
                this.marshaller = new ClosureObjectMarshaller(this.clazz, this.closure.curry(namespace))
            }
            else {
                this.marshaller = new ClosureObjectMarshaller(this.clazz, this.closure)
            }
        }
        this.marshaller
    }
}
