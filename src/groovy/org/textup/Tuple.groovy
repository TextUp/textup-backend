package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.type.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
class Tuple<X, Y> {

    final X first
    final Y second

    Tuple(X arg1, Y arg2) {
        first = arg1
        second = arg2
    }

    static <X, Y> Result<Tuple<X, Y>> tryCreate(X arg1, Y arg2) {
        IOCUtils.resultFactory.success(Tuple.create(arg1, arg2), ResultStatus.CREATED)
    }

    static <X, Y> Tuple<X, Y> create(X arg1, Y arg2) {
        new Tuple<X, Y>(arg1, arg2)
    }

    static <T, X, Y> T split(Collection<Tuple<X, Y>> tuples, Closure<T> action) {
        List<X> firstArgs = []
        List<Y> secondArgs = []
        tuples.each { Tuple<X, Y> tup1 ->
            firstArgs << tup1.first
            secondArgs << tup1.second
        }
        action(firstArgs, secondArgs)
    }

    static <T, X, Y> T split(Tuple<X, Y> tuple, Closure<T> action) {
        action(tuple.first, tuple.second)
    }

    // Methods
    // -------

    Result<Tuple<X, Y>> checkBothPresent() {
        first != null && second != null ?
            IOCUtils.resultFactory.success(this) :
            IOCUtils.resultFactory.failWithCodeAndStatus("tuple.missingData",
                ResultStatus.BAD_REQUEST, [X, Y])
    }
}
