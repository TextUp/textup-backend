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

    boolean hasErrorBeenHandled = false // ensure only one failure handler is called in the chain
    private final List<Object> successArgs = []
    private final List<Object> failureArgs = []

    static <V> Result<V> createSuccess(V payload, ResultStatus status = ResultStatus.OK) {
        new Result<V>(status, payload, [])
    }

    static <V> Result<V> createError(List<String> messages, ResultStatus status) {
        new Result<V>(status, null, messages)
    }

    static Result<Void> "void"() { Result.<Void>createSuccess(null, ResultStatus.NO_CONTENT) }

    // Methods
    // -------

    void alwaysEnd(Closure<?> action) {
        action?.call(this)
    }

    void thenEnd(Closure<?> successAction) {
        then(successAction)
    }

    public <V> Result<V> then(Closure<Result<V>> successAction) {
        getSuccess() ? tryExecuteSuccess(successAction) : this
    }

    public <V> Result<V> ifFail(String prefix, Closure<Result<V>> failAction) {
        tryLogMessageIfFail(prefix)
        ifFail(failAction)
    }

    public <V> Result<V> ifFail(String prefix, LogLevel level, Closure<Result<V>> failAction) {
        tryLogMessageIfFail(prefix, level)
        ifFail(failAction)
    }

    void ifFailEnd(Closure<?> failAction) {
        ifFail(failAction)
    }

    void ifFailEnd(String prefix, Closure<?> failAction) {
        tryLogMessageIfFail(prefix)
        ifFail(failAction)
    }

    void ifFailEnd(String prefix, LogLevel level, Closure<?> failAction) {
        tryLogMessageIfFail(prefix, level)
        ifFail(failAction)
    }

    public <V> Result<V> ifFail(Closure<Result<V>> failAction) {
        if (getSuccess()) {
            this
        }
        else {
            Result<V> handledResult = tryExecuteFailure(failAction)
            hasErrorBeenHandled = true // this Result has been handled
            handledResult?.hasErrorBeenHandled = true // if a different result, has also been handled
            handledResult
        }
    }

    Result<T> logFail(String prefix = "", LogLevel level = LogLevel.ERROR) {
        if (!getSuccess()) {
            tryLogMessage(prefix, level)
            hasErrorBeenHandled = true
        }
        this
    }

    ResultGroup<T> toGroup() { new ResultGroup<T>([this]) }

    Result<T> curry(Object... args) {
        successArgs.addAll(ResultUtils.normalizeVarArgs(args))
        this
    }
    Result<T> curryFailure(Object... args) {
        failureArgs.addAll(ResultUtils.normalizeVarArgs(args))
        this
    }

    Result<T> clearCurry() {
        successArgs.clear()
        this
    }
    Result<T> clearCurryFailure() {
        failureArgs.clear()
        this
    }

    // Properties
    // ----------

    boolean getSuccess() { status.isSuccess }

    // Helpers
    // -------

    protected void tryLogMessageIfFail(String prefix, LogLevel level = LogLevel.ERROR) {
        if (!getSuccess()) {
            tryLogMessage(prefix, level)
        }
    }

    protected void tryLogMessage(String prefix, LogLevel level) {
        if (!hasErrorBeenHandled) {
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
    }

    protected <W> W tryExecuteSuccess(Closure<W> action) {
        if (action && !hasErrorBeenHandled) {
            List<Object> args = new ArrayList<Object>(successArgs)
            args << payload
            args << status
            ClosureUtils.execute(action, args)
        }
        else { this }
    }

    protected <W> W tryExecuteFailure(Closure<W> action) {
        if (action && !hasErrorBeenHandled) {
            List<Object> args = new ArrayList<Object>(failureArgs)
            args << this
            ClosureUtils.execute(action, args)
        }
        else { this }
    }
}
