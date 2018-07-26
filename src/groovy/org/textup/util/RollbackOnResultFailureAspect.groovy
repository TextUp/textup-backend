package org.textup.util

import grails.compiler.GrailsCompileStatic
import groovy.util.logging.Log4j
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.transaction.TransactionStatus
import org.textup.Organization
import org.textup.Result

@GrailsCompileStatic
@Log4j
@Aspect
class RollbackOnResultFailureAspect {

    @AfterReturning(
        pointcut  = "@annotation(rAnnotation)",
        returning = "res")
    def rollbackTransactionOnFailure(RollbackOnResultFailure rAnnotation, Result res) {
        // Can use any class to expose the TransactionStatus object. Business logic should always
        // be encapsulated in a Transasction. In the off chance that it is not, this will create
        // a new transaction and set the only possible outcome of the transaction to rollback any
        // changes that were made. This method is necessary because sometimes we reach an error
        // state and would like to rollback, but unless there is a ValidationError on a domain class,
        // this is not always the case and sometimes we inadvertently persist classes created
        // halfway if the operation fails on a later step
        // We use the Organization class because this domain has relatively few dependencies and the
        // doWithoutFlush method also uses the Organization class so we have to mock only one web
        // of dependencies when testing
        if (!res.success) {
            Organization.withTransaction { TransactionStatus status -> status.setRollbackOnly() }
            res.logFail("Rolling back transaction. Reason:", rAnnotation.value())
        }
    }
}
