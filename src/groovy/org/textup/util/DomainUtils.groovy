package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class DomainUtils {

    // TODO test
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Object getId(Object obj) {
        obj?.metaClass?.hasProperty(obj, "id") ? obj.id : null
    }

    // TODO test
    static boolean isNew(Object obj) {
        getId(obj) == null
    }

    static <T extends Saveable> Result<T> trySave(T obj, ResultStatus status = ResultStatus.OK) {
        if (obj.save()) {
            IOCUtils.resultFactory.success(obj, status)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
    }

    static <T extends Saveable> Result<Void> trySaveAll(Collection<T> objList) {
        ResultGroup<T> resGroup = new ResultGroup<>()
        objList?.each { T obj -> resGroup << DomainUtils.trySave(obj) }
        if (resGroup.anyFailures) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { IOCUtils.resultFactory.success() }
    }
}
