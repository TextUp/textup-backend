package org.textup.rest

import grails.converters.JSON
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class JsonNamedMarshaller extends NamedMarshaller {
    JsonNamedMarshaller(Class clazz, Closure closure) {
        super(JSON, clazz, closure)
    }
}
