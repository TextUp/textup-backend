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
    private hasErrorBeenHandled = false // ensure only one failure handler is called in the chain

    // Static methods
    // --------------

    static <V> Result<V> createSuccess(V payload, ResultStatus status = ResultStatus.OK) {
        Result<V> res = new Result<>()
        res.setSuccess(payload, status)
    }
    static <V> Result<V> createError(List<String> messages, ResultStatus status) {
        Result<V> res = new Result<>()
        res.setError(messages, status)
    }

    // Methods
    // -------

    public void end(Closure<?> successAction, Closure<?> failAction = null) {
        getSuccess() ? tryExecuteSuccess(successAction) : tryExecuteFailure(failAction)
    }

    public <V> Result<V> then(Closure<Result<V>> successAction) {
        getSuccess() ? tryExecuteSuccess(successAction) : tryExecuteFailure(null)
    }

    public <V> Result<V> ifFail(Closure<Result<V>> failAction) {
        getSuccess() ? tryExecuteSuccess(null) : tryExecuteFailure(failAction)
    }

    public <V> Result<V> ifFail(String prefix, Closure<Result<V>> failAction) {
        logFail(prefix)
        ifFail(failAction)
    }

    public <V> Result<V> ifFail(String prefix, LogLevel level, Closure<Result<V>> failAction) {
        logFail(prefix, level)
        ifFail(failAction)
    }

    Result<T> logFail(String prefix = "", LogLevel level = LogLevel.ERROR) {
        if (!getSuccess()) {
            String statusString = status.intStatus.toString()
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
        status.isSuccess
    }
    Result<T> setSuccess(T success, ResultStatus status = ResultStatus.OK) {
        status = status
        payload = success
        this
    }
    Result<T> setError(List<String> errors, ResultStatus status) {
        status = status
        errorMessages = errors
        this
    }

    // Helpers
    // -------

    protected <W> W tryExecuteSuccess(Closure<W> action) {
        if (action) {
            List<Object> args = new ArrayList<Object>(successArgs)
            args << payload
            args << status
            execute(action, args)
        }
        else { this }
    }
    protected <W> W tryExecuteFailure(Closure<W> action) {
        if (action && !hasErrorBeenHandled) {
            hasErrorBeenHandled = true
            List<Object> args = new ArrayList<Object>(failureArgs)
            args << this
            execute(action, args)
        }
        else { this }
    }
    protected <W> W execute(Closure<W> action, List<Object> args) {
        int maxNumArgs = action.maximumNumberOfParameters
        List<Object> validArgs = ResultUtils.buildArgs(maxNumArgs, args)
        ResultUtils.callClosure(action, validArgs.toArray())
    }
}
