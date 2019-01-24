package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class ClosureUtils {

    // Do not have args optional
    // Generic type checking seems to not play well with Groovy default prop values
    static <T> T execute(Closure<T> action, List<?> args) {
        int maxNumArgs = action.maximumNumberOfParameters
        Object[] adjustedArgs = buildArgs(maxNumArgs, args).toArray()
        switch (adjustedArgs.length) {
            case 0: return action.call()
            case 1: return action.call(adjustedArgs[0])
            default: return action.call(adjustedArgs)
        }
    }

    // Helpers
    // -------

    static List<Object> buildArgs(int maxNumArgs, List<?> args) {
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
}
