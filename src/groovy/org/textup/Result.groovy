package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Log4j
import org.textup.type.LogLevel

@GrailsCompileStatic
@Log4j
@ToString
@EqualsAndHashCode
class Result<T> {

    T payload
    ResultStatus status = ResultStatus.OK
    List<String> errorMessages = []

    // Static methods
    // --------------

    static <V> Result<V> createSuccess(T payload, ResultStatus status) {
        Result<V> res = new Result<>()
        res.setSuccess(payload, status)
    }
    static <V> Result<V> createError(List<String> messages, ResultStatus status) {
        Result<V> res = new Result<>()
        res.setError(messages, status)
    }

    // Methods
    // -------

    public void thenEnd(Closure<?> successAction) {
        if (this.success) {
            executeSuccess(successAction)
        }
    }
    public void thenEnd(Closure<?> successAction, Closure<?> failAction) {
        this.success ? executeSuccess(successAction) : executeFailure(failAction)
    }

    public <V> Result<V> then(Closure<Result<V>> successAction) {
        if (this.success) {
            executeSuccess(successAction)
        }
        else { Result.<V>createError(this.errorMessages, this.status) }
    }
    public <V> Result<V> then(Closure<Result<V>> successAction, Closure<Result<V>> failAction) {
        this.success ? executeSuccess(successAction) : executeFailure(failAction)
    }

    Result<T> logFail(String prefix = "", LogLevel level = LogLevel.ERROR) {
        if (!this.success) {
            String statusString = this.status.intStatus.toString()
            String msg = prefix ? "${prefix}: ${statusString}: ${errorMessages}" : "${statusString}: ${errorMessages}"
            switch (level) {
                case LogLevel.DEBUG:
                    log.debug(msg)
                    break
                case LogLevel.INFO:
                    log.info(msg)
                    break
                default: // LogLevel.ERROR
                    log.error(msg)
            }
        }
        this
    }

    ResultGroup<T> toGroup() {
        (new ResultGroup<>()).add(this)
    }

    // Property Access
    // ---------------

    boolean getSuccess() {
        this.status.isSuccess
    }
    Result<T> setSuccess(T success, ResultStatus status = ResultStatus.OK) {
        this.status = status
        this.payload = success
        this
    }
    Result<T> setError(List<String> errors, ResultStatus status) {
        this.status = status
        this.errorMessages = errors
        this
    }

    // Helpers
    // -------

    protected <W> W executeSuccess(Closure<W> action) {
    	if (action.maximumNumberOfParameters == 0) {
            action()
        }
        else if (action.maximumNumberOfParameters == 1) {
            action(payload)
        }
        else { // action.maximumNumberOfParameters == 2
            action(payload, status)
        }
    }
    protected <W> W executeFailure(Closure<W> action) {
        if (action.maximumNumberOfParameters == 0) {
            action()
        }
        else if (action.maximumNumberOfParameters == 1) {
            action(errorMessages)
        }
        else { // action.maximumNumberOfParameters == 2
            action(errorMessages, status)
        }
    }
}
