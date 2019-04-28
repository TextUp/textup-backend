package org.textup.structure

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
interface HandlesActions<I, O> {
    boolean hasActions(Map body)
    Result<O> tryHandleActions(I input, Map body)
}
