package org.textup.rest 

import grails.converters.JSON 

class JsonNamedMarshaller extends NamedMarshaller {
    JsonNamedMarshaller(Class clazz, Closure closure) {
        super(JSON, clazz, closure)
    }
}