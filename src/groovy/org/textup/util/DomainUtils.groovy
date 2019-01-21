package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class DomainUtils {

    // TODO test
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Object tryGetId(Object obj) {
        obj?.metaClass?.hasProperty(obj, "id") ? obj.id : null
    }

    // TODO test
    static boolean isNew(Object obj) {
        getId(obj) == null
    }

    // TODO skip type checking
    static boolean hasDirtyNonObjectFields(Object obj, Collection<String> propsToIgnore) {
        if (obj.metaClass.hasProperty("dirtyPropertyNames")) {
            List<String> dirtyProps = obj.dirtyPropertyNames
            !dirtyProps.isEmpty() &&
                dirtyProps.findAll { !propsToIgnore.contains(it) }.size() > 0
        }
        else { false }
    }

    // TODO null handling, return error if null is passed in
    static <T extends Saveable> Result<T> trySave(T obj, ResultStatus status = ResultStatus.OK) {
        if (obj.save()) {
            IOCUtils.resultFactory.success(obj, status)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
    }

    // TODO null handling, return error if null is passed in
    static <T extends Saveable> Result<Void> trySaveAll(Collection<T> objList) {
        ResultGroup<T> resGroup = new ResultGroup<>()
        objList?.each { T obj -> resGroup << DomainUtils.trySave(obj) }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { Result.void() }
    }

    // TODO null handling, return error if null is passed in
    static <T extends Validateable> Result<T> tryValidate(T obj,
        ResultStatus status = ResultStatus.OK) {

        if (obj.validate()) {
            IOCUtils.resultFactory.success(obj, status)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
    }

    // TODO null handling, return error if null is passed in
    static <T extends Validateable> Result<Void> tryValidateAll(Collection<T> objList) {
        ResultGroup<T> resGroup = new ResultGroup<>()
        objList?.each { T obj -> resGroup << DomainUtils.tryValidate(obj) }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { Result.void() }
    }
}
