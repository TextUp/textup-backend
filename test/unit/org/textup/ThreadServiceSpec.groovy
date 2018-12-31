package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import java.util.concurrent.*
import spock.lang.Specification

// NOTE: Services under test are NOT singletons. This tests creates its own instance
// that is DIFFERENT than the one that has its init/destroy methods automatically called.
// Therefore, we need to manually create and destroy in this test
// [FUTURE] figure out how to test transactional code executed within a new session.
// After trying to test the transactional behavior of threadService in a non-transactional
// integration test, we were not able to get the changes in the new session to become visible
// to the test class

@TestFor(ThreadService)
@Domain([CustomAccountDetails, Organization, Location])
@TestMixin(HibernateTestMixin)
class ThreadServiceSpec extends Specification {

    def setup() {
        service.startPool()
    }

    def cleanup() {
        service.cleanUp()
    }

    void "test wrapping action in new session"() {
        when:
        Future<Boolean> future = service.submit {
            Boolean hasSession
            Organization.withSession { hasSession = !!it }
            hasSession
        }

        then:
        future.get() == true
        future.isCancelled() == false
        future.isDone() == true
    }

    void "test wrapping action in new session with delayed execution"() {
        when:
        ScheduledFuture<Boolean> schedFuture = service.delay(2, TimeUnit.SECONDS) {
            Boolean hasSession
            Organization.withSession { hasSession = !!it }
            hasSession
        }

        then:
        schedFuture.getDelay(TimeUnit.MILLISECONDS) > 0 // returns the remaining delay left
        schedFuture.get() == true
        schedFuture.isCancelled() == false
        schedFuture.isDone() == true
    }

    void "test catching uncaught exceptions"() {
        when:
        Future<Boolean> future = service.submit { throw new NullPointerException("testing") }

        then: "error is logged and future is NOT cancelled because exception is caught"
        future.get() == null
        future.isCancelled() == false
        future.isDone() == true
    }
}
