package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.transform.EqualsAndHashCode

@GrailsCompileStatic
@EqualsAndHashCode
class Tuple<X, Y> {

    private final X first
    private final Y second

    Tuple(X x, Y, y) {
        first = x
        second = y
    }

    X getFirst() { first }
    Y getSecond() { second }
}
