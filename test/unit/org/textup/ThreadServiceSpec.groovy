package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import java.util.concurrent.*
import spock.lang.Specification

// NOTE: Services under test are NOT singletons. This tests creates its own instance
// that is DIFFERENT than the one that has its init/destroy methods automatically called.
// Therefore, we need to manually create and destroy in this test

@TestFor(ThreadService)
@Domain([Organization, Location])
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

    void "test catching uncaught exceptions"() {
        when:
        Future<Boolean> future = service.submit { throw new NullPointerException("testing") }

        then: "error is logged and future is NOT cancelled because exception is caught"
        future.get() == null
        future.isCancelled() == false
        future.isDone() == true
    }
}
