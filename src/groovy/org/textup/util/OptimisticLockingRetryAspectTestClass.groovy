package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.hibernate.StaleObjectStateException
import org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException
import org.textup.*

@GrailsTypeChecked
class OptimisticLockingRetryAspectTestClass {

    int numTimesCalled = 0

    @OptimisticLockingRetry(retryCount = 2)
    void throwsException() {
        numTimesCalled++
        throw new HibernateOptimisticLockingFailureException(
            new StaleObjectStateException("org.textup.RecordItemReceipt", 88))
    }
}
