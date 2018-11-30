package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Log4j
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
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

    public void thenEnd(Closure<?> successAction, Closure<?> failAction = null) {
        this.success ? executeSuccess(successAction) : executeFailure(failAction)
    }

    public <V> Result<V> then(Closure<Result<V>> successAction, Closure<Result<V>> failAction = null) {
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
        this
    }
    Result<T> curryFailure(Object... args) {
        failureArgs.addAll(args)
        this
    }

    Result<T> clearCurry() {
        clearCurrySuccess()
        clearCurryFailure()
    }
    Result<T> clearCurrySuccess() {
        successArgs.clear()
        this
    }
    Result<T> clearCurryFailure() {
        failureArgs.clear()
        this
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
        List<Object> args = new ArrayList<Object>(successArgs)
        args << payload
        args << status
        execute(action, args)
    }
    protected <W> W executeFailure(Closure<W> action) {
        List<Object> args = new ArrayList<Object>(failureArgs)
        args << this
        execute(action, args)
    }
    protected <W> W execute(Closure<W> action, List<Object> args) {
        if (!action) {
            return this
        }
        int maxNumArgs = action.maximumNumberOfParameters
        List<Object> builtArgs = buildArgs(maxNumArgs, args)
        Utils.callClosure(action, builtArgs.toArray())
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
            args.addAll(Collections.nCopies(maxNumArgs - numArgs, null))
            args
        }
    }
}
