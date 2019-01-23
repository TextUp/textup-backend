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

    static List<Object> buildArgs(int maxNumArgs, List<Object> args) {
        int numArgs = args.size()
        if (maxNumArgs == 0) {
            []
        }
        else if (numArgs == maxNumArgs) {
            args
        }
        else if (numArgs > maxNumArgs) {
            args[0..(maxNumArgs - 1)]
        }
        else { // numArgs < maxNumArgs
            args.addAll(Collections.nCopies(maxNumArgs - numArgs, null))
            args
        }
    }

    static <T> T callClosure(Closure<T> action, Object[] args) {
        switch (args.length) {
            case 0: return action.call()
            case 1: return action.call(args[0])
            default: return action.call(args)
        }
    }

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
            IOCUtils.resultFactory.failWithGroup(this)
        }
        else { // someSuccess && someFailure
            if (allowSomeFailures) {
                successRes
            }
            else {
                IOCUtils.resultFactory.failWithGroup(this)
            }
        }
    }
}
