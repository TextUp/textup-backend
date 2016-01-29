package org.textup

import groovy.transform.ToString
import groovy.util.logging.Log4j

@Log4j
@ToString
class Result<T> {
    boolean success = true
    T payload
    ResultType type //check the Constants class for valid types

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

    Collection<String> getErrorMessages() {
        Collection<String> messages = []
        switch (res.type) {
            case ResultType.VALIDATION:
                res.payload.allErrors.each {
                    messages << messageSource.getMessage(it, LCH.getLocale())
                }
                break
            case ResultType.MESSAGE_STATUS:
                messages << res.payload.message
                break
            case ResultType.MESSAGE_LIST_STATUS:
                messages += res.payload.messages
                break
            case ResultType.THROWABLE:
                messages << res.payload.message
                break
            case ResultType.MESSAGE:
                messages << res.payload.message
                break
        }
        messages
    }

    protected def executeAction(Closure action) {
    	if (action.maximumNumberOfParameters == 0) { action() }
        else (action.maximumNumberOfParameters == 1) { action(payload) }
        else { action(type, payload) }
    }

    // Multiple
    // --------

    static <E> Result<E> waterfall(Closure<Result>... actions) {
        if (!actions) { return null }
        Result prevRes, thisRes
        for (action in actions) {
            if (!prevRes) {
                thisRes = action()
            }
            else if (prevRes.success) {
                thisRes = action(prevRes.payload)
            }
            else { // prev result is failure
                return prevRes
            }
            prevRes = thisRes
        }
        thisRes
    }
}
