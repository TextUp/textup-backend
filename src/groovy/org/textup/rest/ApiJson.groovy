package org.textup.rest

import grails.converters.JSON
import org.codehaus.groovy.grails.web.json.JSONWriter

class ApiJson extends JSON {

    ApiJson() { super() }

    ApiJson(Object target) { super(target) }

    void renderPartial(JSONWriter out) {
        super.writer = out
        super.referenceStack = new Stack<Object>()
        super.value(target)
    }
}
