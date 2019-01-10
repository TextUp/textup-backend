package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode

@GrailsTypeChecked
@EqualsAndHashCode
class Tuple<X, Y> {

    private final X first
    private final Y second

    Tuple(X arg1, Y arg2) {
        first = arg1
        second = arg2
    }

    static <X, Y> Tuple<X, Y> create(X arg1, Y arg2) {
        new Tuple<X, Y>(arg1, arg2)
    }

    // Methods
    // -------

    Result<Tuple<X. Y>> checkBothPresent() {
        first != null && second != null ?
            IOCUtils.resultFactory.success(this) : // TODO
            IOCUtils.resultFactory.failWithCodeAndStatus("tuple.missingData", ResultStatus.BAD_REQUEST)
    }

    // Properties
    // ----------

    X getFirst() { first }
    Y getSecond() { second }
}
