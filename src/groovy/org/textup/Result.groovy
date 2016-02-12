package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.ToString
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.types.ResultType

@GrailsCompileStatic
@Log4j
@ToString
class Result<T> {

    // set by resultFactory upon creation
    MessageSource messageSource

    boolean success = true
    T payload
    ResultType type

    // Individual
    // ----------

    def then(Closure successAction) {
    	this.success ? executeAction(successAction) : this
    }

    def then(Closure successAction, Closure failAction) {
    	this.success ? executeAction(successAction) : executeAction(failAction)
    }

    Result logFail(String prefix="") {
        if (!this.success) {
            log.error(prefix ? "${prefix}: ${type}: ${payload}" : "${type}: ${payload}")
        }
        this
    }

    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    Collection<String> getErrorMessages() {
        Collection<String> messages = []
        try {
            switch (this.type) {
                case ResultType.VALIDATION:
                    this.payload.allErrors.each {
                        messages << messageSource.getMessage(it, LCH.getLocale())
                    }
                    break
                case ResultType.MESSAGE_STATUS:
                    messages << this.payload.message
                    break
                case ResultType.MESSAGE_LIST_STATUS:
                    messages += this.payload.messages
                    break
                case ResultType.THROWABLE:
                    messages << this.payload.message
                    break
                case ResultType.MESSAGE:
                    messages << this.payload.message
                    break
            }
        }
        catch (e) {
            log.error("Result.getErrorMessages: Could not get errors for type \
                ${this.type} with errors ${e.message}")
        }
        messages
    }

    protected def executeAction(Closure action) {
    	if (action.maximumNumberOfParameters == 0) { action() }
        else if (action.maximumNumberOfParameters == 1) { action(payload) }
        else { action(type, payload) }
    }

    // Multiple
    // --------

    static <E> Result<E> waterfall(Closure<Result>... actions) {
        if (!actions) { return null }
        Result prevRes, thisRes
        for (action in actions) {
            if (!prevRes) {
                thisRes = action() as Result
            }
            else if (prevRes.success) {
                thisRes = action(prevRes.payload) as Result
            }
            else { // prev result is failure
                return prevRes
            }
            prevRes = thisRes
        }
        thisRes
    }
}
