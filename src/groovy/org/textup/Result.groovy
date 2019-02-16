package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.TupleConstructor
import groovy.util.logging.Log4j
import org.textup.type.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
@Log4j
@ToString
@TupleConstructor(includeFields = true, includes = ["status", "payload", "errorMessages"])
class Result<T> {

    final ResultStatus status
    final T payload
    final List<String> errorMessages

    private final List<Object> successArgs = []
    private final List<Object> failureArgs = []
    private hasErrorBeenHandled = false // ensure only one failure handler is called in the chain

    static <V> Result<V> createSuccess(V payload, ResultStatus status = ResultStatus.OK) {
        new Result<V>(status, payload, [])
    }

    static <V> Result<V> createError(List<String> messages, ResultStatus status) {
        new Result<V>(status, null, messages)
    }

    static Result<Void> "void"() { Result.<Void>createSuccess(null, ResultStatus.NO_CONTENT) }

    // Methods
    // -------

    void thenEnd(Closure<?> successAction) {
        getSuccess() ? tryExecuteSuccess(successAction) : null
    }

    void alwaysEnd(Closure<?> action) {
        action?.call(this)
    }

    public <V> Result<V> then(Closure<Result<V>> successAction) {
        getSuccess() ? tryExecuteSuccess(successAction) : this
    }

    public <V> Result<V> ifFail(Closure<Result<V>> failAction) {
        getSuccess() ? this : tryExecuteFailure(failAction)
    }

    public <V> Result<V> ifFail(String prefix, Closure<Result<V>> failAction) {
        logFail(prefix)
        ifFail(failAction)
    }

    public <V> Result<V> ifFail(String prefix, LogLevel level, Closure<Result<V>> failAction) {
        logFail(prefix, level)
        ifFail(failAction)
    }

    void ifFailEnd(Closure<?> failAction) {
        getSuccess() ? this : tryExecuteFailure(failAction)
    }

    void ifFailEnd(String prefix, Closure<?> failAction) {
        logFail(prefix)
        ifFailEnd(failAction)
    }

    void ifFailEnd(String prefix, LogLevel level, Closure<?> failAction) {
        logFail(prefix, level)
        ifFailEnd(failAction)
    }

    Result<T> logFail(String prefix = "", LogLevel level = LogLevel.ERROR) {
        if (!getSuccess()) {
            String statusString = status.intStatus.toString()
            String msg = prefix ?
                "${prefix}: ${statusString}: ${errorMessages}" :
                "${statusString}: ${errorMessages}"
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

    ResultGroup<T> toGroup() { new ResultGroup<T>([this]) }

    // Currying
    // --------

    Result<T> curry(Object... args) {
        currySuccess(args)
        curryFailure(args)
    }
    Result<T> currySuccess(Object... args) {
        successArgs.addAll(ResultUtils.normalizeVarArgs(args))
        this
    }
    Result<T> curryFailure(Object... args) {
        failureArgs.addAll(ResultUtils.normalizeVarArgs(args))
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

    boolean hasErrorBeenHandled() { hasErrorBeenHandled }

    void resetErrorHandling() {
        hasErrorBeenHandled = false
    }

    // Properties
    // ----------

    boolean getSuccess() { status.isSuccess }

    // Helpers
    // -------

    protected <W> W tryExecuteSuccess(Closure<W> action) {
        if (action) {
            List<Object> args = new ArrayList<Object>(successArgs)
            args << payload
            args << status
            ClosureUtils.execute(action, args)
        }
        else { this }
    }

    protected <W> W tryExecuteFailure(Closure<W> action) {
        if (action && !hasErrorBeenHandled) {
            hasErrorBeenHandled = true
            List<Object> args = new ArrayList<Object>(failureArgs)
            args << this
            ClosureUtils.execute(action, args)
        }
        else { this }
    }
}
