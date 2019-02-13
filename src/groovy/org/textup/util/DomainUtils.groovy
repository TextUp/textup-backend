package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class DomainUtils {

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Object tryGetId(Object obj) {
        obj?.metaClass?.hasProperty(obj, "id") ? obj.id : null
    }

    static boolean isNew(Object obj) {
        obj ? (tryGetId(obj) == null) : false
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static boolean hasDirtyNonObjectFields(Object obj, Collection<String> propsToIgnore = []) {
        if (obj.metaClass.respondsTo(obj, "getDirtyPropertyNames")) {
            List<String> dirtyProps = obj.dirtyPropertyNames
            !dirtyProps.isEmpty() &&
                dirtyProps.findAll { !propsToIgnore?.contains(it) }.size() > 0
        }
        else { false }
    }

     static <T extends CanSave> Result<T> trySave(T obj, ResultStatus status = ResultStatus.OK) {
        if (obj) {
            if (obj.save()) {
                IOCUtils.resultFactory.success(obj, status)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
        }
        else { invalidInput() }
    }

    static <T extends CanSave> Result<Void> trySaveAll(Collection<T> objList) {
        if (objList != null) {
            ResultGroup<T> resGroup = new ResultGroup<>()
            objList.each { T obj -> resGroup << DomainUtils.trySave(obj) }
            if (resGroup.anyFailures) {
                IOCUtils.resultFactory.failWithGroup(resGroup)
            }
            else { Result.void() }
        }
        else { invalidInput() }
    }

    static <T extends CanValidate> Result<T> tryValidate(T obj,
        ResultStatus status = ResultStatus.OK) {

        if (obj) {
            if (obj.validate()) {
                IOCUtils.resultFactory.success(obj, status)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(obj.errors) }
        }
        else { invalidInput() }
    }

    static <T extends CanValidate> Result<Void> tryValidateAll(Collection<T> objList) {
        if (objList != null) {
            ResultGroup<T> resGroup = new ResultGroup<>()
            objList.each { T obj -> resGroup << DomainUtils.tryValidate(obj) }
            if (resGroup.anyFailures) {
                IOCUtils.resultFactory.failWithGroup(resGroup)
            }
            else { Result.void() }
        }
        else { invalidInput() }
    }

    // Helpers
    // -------

    protected static Result<?> invalidInput() {
        IOCUtils.resultFactory.failWithCodeAndStatus("domainUtils.invalidInput",
            ResultStatus.INTERNAL_SERVER_ERROR)
    }
}
