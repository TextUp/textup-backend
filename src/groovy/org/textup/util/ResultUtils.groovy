package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class ResultUtils {

    static <T> Result<T> convertGroupToResult(ResultGroup<?> resGroup, Result<T> successRes,
        boolean allowSomeFailures) {

        boolean someSuccess = resGroup.anySuccesses,
            someFailure = resGroup.anyFailures
        if (!someSuccess && !someFailure) {
            successRes
        }
        else if (someSuccess && !someFailure) {
            successRes
        }
        else if (!someSuccess && someFailure) {
            IOCUtils.resultFactory.failWithGroup(resGroup)
        }
        else { // someSuccess && someFailure
            if (allowSomeFailures) {
                successRes
            }
            else {
                IOCUtils.resultFactory.failWithGroup(resGroup)
            }
        }
    }
}
