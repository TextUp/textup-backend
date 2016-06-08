package org.textup.util

import grails.compiler.GrailsCompileStatic
import groovy.util.logging.Log4j
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.ProceedingJoinPoint
import org.hibernate.StaleObjectStateException
import org.springframework.core.Ordered
import org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException

@GrailsCompileStatic
@Log4j
@Aspect
class OptimisticLockingRetryAspect implements Ordered {

	int order = -1

	@Around(value="@annotation(optimisticLockingRetry)", argNames = "optimisticLockingRetry")
	def retry(ProceedingJoinPoint pjp, OptimisticLockingRetry optimisticLockingRetry)
		throws Throwable {
		Integer allowedRetries = optimisticLockingRetry.retryCount(),
			retriesSoFar = 0
		def result
		while (true) {
			try {
				result = pjp.proceed()
				break
			}
			catch (StaleObjectStateException |
	            HibernateOptimisticLockingFailureException e) {
	            log.debug("OPTIMISTIC LOCKING EXCEPTION after $retriesSoFar retries \
	                e.message: e.class: ${e.class}, ${e.message}, e: $e")
	            if (allowedRetries < retriesSoFar) {
	            	retriesSoFar++
            	}
            	else { throw e }
	        }
		}
        result
	}
}