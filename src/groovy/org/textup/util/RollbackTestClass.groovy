package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*

@GrailsTypeChecked
@Transactional
class RollbackTestClass {

    Result<?> notAnnotated(Closure<Result<?>> doAction) { doAction() }

    @RollbackOnResultFailure
    Result<?> correctReturn(Closure<Result<?>> doAction) { doAction() }

    @RollbackOnResultFailure
    boolean incorrectReturn(Closure<Boolean> doAction) { doAction() }
}
