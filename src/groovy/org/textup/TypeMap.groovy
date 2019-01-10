package org.textup

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.web.util.TypeConvertingMap

@GrailsTypeChecked
class TypeMap extends TypeConvertingMap {

    TypeMap(Object obj) {
        super(TypeConvertingUtils.to(Map, obj))
    }

    TypeMap(Map map = null) { // superclass constructor handles nulls
        super(map)
    }

    String "string"(String propName, String fallbackVal = null) {
        TypeConvertingUtils.to(String, get(propName), fallbackVal)
    }

    public <T> T "enum"(Class<T> enumClass, String propName, T fallbackVal = null) {
        TypeConversionUtils.convertEnum(enumClass, get(propName)) ?: fallbackVal
    }

    TypeMap typeMapNoNull(String propName) {
        new TypeMap(get(propName))
    }
}
