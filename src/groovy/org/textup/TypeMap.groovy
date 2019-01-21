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

    static TypeMap create(Map map) {
        new TypeMap(map)
    }

    static Result<TypeMap> tryCreate(Map map) {
        IOCUtils.resultFactory.success(TypeMap.create(map), ResultStatus.CREATED)
    }

    // Methods
    // -------

    String "string"(String propName, String fallbackVal = null) {
        TypeConvertingUtils.to(String, get(propName), fallbackVal)
    }

    public <T> T "enum"(Class<T> enumClass, String propName, T fallbackVal = null) {
        TypeConversionUtils.convertEnum(enumClass, get(propName)) ?: fallbackVal
    }

    TypeMap typeMapNoNull(String propName) {
        new TypeMap(get(propName))
    }

    DateTime dateTime(String propName, String timezone = null, DateTime fallbackVal = null) {
        DateTimeUtils.toDateTimeWithZone(get(propName), timezone) ?: fallbackVal
    }

    public <T> List<T> typedList(Class<T> clazz, String propName, List<T> fallbackVal = null) {
        List<?> vals = list(propName)
        vals ? TypeConversionUtils.allTo(clazz, vals) : fallbackVal
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

    public <T> List<T> toEnumList(Class<T> enumClass, String propName, List<T> fallbackVal = null) {
        TypeConversionUtils.toEnumList(enumClass, list(propName), fallbackVal)
    }
}
