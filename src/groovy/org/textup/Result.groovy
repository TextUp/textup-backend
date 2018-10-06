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

    private final List<Object> successArgs = []
    private final List<Object> failureArgs = []

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
            String msg = prefix
                ? "${prefix}: ${statusString}: ${errorMessages}"
                : "${statusString}: ${errorMessages}"
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

    // Currying
    // --------

    Result<T> curry(Object... args) {
        currySuccess(args)
        curryFailure(args)
    }
    Result<T> currySuccess(Object... args) {
        successArgs.addAll(args)
    }
    Result<T> curryFailure(Object... args) {
        failureArgs.addAll(args)
    }

    Result<T> clearCurry() {
        clearCurrySuccess()
        clearCurryFailure()
    }
    Result<T> clearCurrySuccess() {
        successArgs.clear()
    }
    Result<T> clearCurryFailure() {
        failureArgs.clear()
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
        execute(action, successArgs + [payload, status])
    }
    protected <W> W executeFailure(Closure<W> action) {
        execute(action, failureArgs + [this])
    }
    protected <W> W execute(Closure<W> action, List<Object> args) {
        if (!action) {
            return this
        }
        int maxNumArgs = action.maximumNumberOfParameters
        action.call(buildArgs(maxNumArgs, args))
    }
    protected List<Object> buildArgs(int maxNumArgs, List<Object> args) {
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
            args + Collections.nCopies(maxNumArgs - numArgs, null)
        }
    }
}
