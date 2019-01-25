package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.textup.util.*
import org.textup.validator.*
import org.textup.type.*

@EqualsAndHashCode
@GrailsTypeChecked
class TypeMap extends TypeConvertingMap {

    TypeMap(Object obj) {
        super(TypeUtils.to(Map, obj))
    }

    TypeMap(Map map = null) { // superclass constructor handles nulls
        super(map)
    }

    static TypeMap create(Object obj) {
        new TypeMap(obj)
    }

    static TypeMap create(Map map) {
        new TypeMap(map)
    }

    static Result<TypeMap> tryCreate(Object obj) {
        IOCUtils.resultFactory.success(TypeMap.create(obj), ResultStatus.CREATED)
    }

    static Result<TypeMap> tryCreate(Map map) {
        IOCUtils.resultFactory.success(TypeMap.create(map), ResultStatus.CREATED)
    }

    // Methods
    // -------

    String "string"(String propName, String fallbackVal = null) {
        TypeUtils.to(String, get(propName), fallbackVal)
    }

    TypeMap typeMapNoNull(String propName) {
        new TypeMap(get(propName))
    }

    DateTime dateTime(String propName, String timezone = null, DateTime fallbackVal = null) {
        JodaUtils.toDateTimeWithZone(get(propName), timezone) ?: fallbackVal
    }

    List<PhoneNumber> phoneNumberList(String propName, List<PhoneNumber> fallbackVal = null) {
        List<?> vals = list(propName)
        if (vals) {
            ResultGroup<PhoneNumber> resGroup = new ResultGroup<>()
            vals.each { Object val -> resGroup << PhoneNumber.tryCreate(val as String) }
            resGroup.payload
        }
        else { fallbackVal }
    }

    // fallback values of type List<T> don't seem to type check
    // Generic type checking seems to not play well with Groovy default prop values
    public <T> List<T> typedList(Class<T> clazz, String propName) {
        List<?> vals = list(propName)
        vals ? TypeUtils.allTo(clazz, vals) : []
    }

    // fallback values of type T don't seem to type check
    // Generic type checking seems to not play well with Groovy default prop values
    public <T extends Enum<T>> T "enum"(Class<T> enumClass, String propName) {
        TypeUtils.convertEnum(enumClass, get(propName)) ?: null
    }

    // fallback values of type List<T> don't seem to type check
    // Generic type checking seems to not play well with Groovy default prop values
    public <T extends Enum<T>> List<T> enumList(Class<T> enumClass, String propName) {
        TypeUtils.toEnumList(enumClass, list(propName))
    }
}
