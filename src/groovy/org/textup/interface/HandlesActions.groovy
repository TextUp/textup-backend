package org.textup.interface

import grails.compiler.GrailsTypeChecked

// TODO propagate

@GrailsTypeChecked
interface HandlesActions<I, O> {
    boolean hasActions(Map body)
    Result<O> tryHandleActions(I input, Map body)
}
