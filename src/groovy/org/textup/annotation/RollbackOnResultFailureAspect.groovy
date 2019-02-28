package org.textup.annotation

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.ProceedingJoinPoint
import org.springframework.transaction.TransactionStatus
import org.textup.*

@GrailsTypeChecked
@Log4j
@Aspect
class RollbackOnResultFailureAspect {

    @Around(value="@annotation(rAnnotation)", argNames = "rAnnotation")
    def rollbackTransactionOnFailure(ProceedingJoinPoint pjp, RollbackOnResultFailure rAnnotation) {
        Object resObj
        // Can use any class to expose the TransactionStatus object. Business logic should always
        // be encapsulated in a Transaction. In the off chance that it is not, this will create
        // a new transaction and set the only possible outcome of the transaction to rollback any
        // changes that were made. This method is necessary because sometimes we reach an error
        // state and would like to rollback, but unless there is a ValidationError on a domain class,
        // this is not always the case and sometimes we inadvertently persist classes created
        // halfway if the operation fails on a later step
        // We use the Organization class because this domain has relatively few dependencies and the
        // doWithoutFlush method also uses the Organization class so we have to mock only one web
        // of dependencies when testing
        Organization.withTransaction { TransactionStatus status ->
            resObj = pjp.proceed()
            if (resObj instanceof Result) {
                Result res = resObj as Result
                if (!res.success) {
                    status.setRollbackOnly()
                    res.logFail("Rolling back transaction. Reason:", rAnnotation.value())
                }
            }
        }
        resObj
    }
}
