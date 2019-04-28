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
        tryExecuteSuccess(successAction)
    }

    public <V> Result<V> then(Closure<Result<V>> successAction) {
        tryExecuteSuccess(successAction)
    }

    Result<T> ifFailAndPreserveError(Closure<?> failAction) {
        tryExecuteFailure(failAction)
        this
    }
    Result<T> ifFailAndPreserveError(String prefix, Closure<?> failAction) {
        tryLogMessage(prefix)
        ifFailAndPreserveError(failAction)
    }
    Result<T> ifFailAndPreserveError(String prefix, LogLevel level, Closure<?> failAction) {
        tryLogMessage(prefix, level)
        ifFailAndPreserveError(failAction)
    }

    public <V> Result<V> ifFail(Closure<Result<V>> failAction) {
        tryExecuteFailure(failAction)
    }
    public <V> Result<V> ifFail(String prefix, Closure<Result<V>> failAction) {
        tryLogMessage(prefix)
        ifFail(failAction)
    }
    public <V> Result<V> ifFail(String prefix, LogLevel level, Closure<Result<V>> failAction) {
        tryLogMessage(prefix, level)
        ifFail(failAction)
    }

    Result<T> logFail(String prefix = "", LogLevel level = LogLevel.ERROR) {
        tryLogMessage(prefix, level)
        // set flag AFTER attempting to log or else we'll never log
        if (!getSuccess()) {
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

    protected void tryLogMessage(String prefix, LogLevel level = LogLevel.ERROR) {
        if (!getSuccess() && !hasErrorBeenHandled) {
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
        if (getSuccess() && action && !hasErrorBeenHandled) {
            List<Object> args = new ArrayList<Object>(successArgs)
            args << payload
            args << status
            ClosureUtils.execute(action, args)
        }
        else { this }
    }

    protected <W> W tryExecuteFailure(Closure<W> action) {
        if (!getSuccess() && action && !hasErrorBeenHandled) {
            List<Object> args = new ArrayList<Object>(failureArgs)
            args << this
            Object retVal = ClosureUtils.execute(action, args)
            // set after conditional
            hasErrorBeenHandled = true
            // if retVal is also result has also been handled NO MATTER THE SUCCESS STATE
            if (retVal instanceof Result) {
                retVal.hasErrorBeenHandled = true
            }
            retVal
        }
        else { this }
    }
}
