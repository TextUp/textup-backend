package org.textup.annotation

import java.util.concurrent.*
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hibernate.StaleObjectStateException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class OptimisticLockingRetryAspectIntegrationSpec extends Specification {

    GrailsApplication grailsApplication
    ThreadService threadService

    def setup() {
        // in order for the Transactional annotation on the test class bean to be propertly
        // configured, we cannot manually instantiate the bean. Instead, we must inject the bean
        // via dependency injection or else we will get NPEs when trying to call any method
        // See https://blog.10ne.org/2014/01/08/registering-new-spring-beans-in-grails-during-runtime/
        GenericBeanDefinition beanDef = new GenericBeanDefinition(beanClass: OptimisticLockingRetryAspectTestClass,
            autowireMode: AbstractBeanDefinition.AUTOWIRE_BY_NAME)
        grailsApplication.mainContext.registerBeanDefinition('optimisticLockingTestClass', beanDef)
    }

    void "test optimistic locking retry annotation"() {
        given:
        OptimisticLockingRetryAspectTestClass testClass =
            grailsApplication.mainContext.getBean(OptimisticLockingRetryAspectTestClass)
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when: "directly calling in this thread"
        testClass.throwsException()

        then:
        testClass.numTimesCalled == 3 // initial call + 2 retries
        thrown HibernateOptimisticLockingFailureException

        when: "calling annotated method in a new thread"
        Future future = threadService.submit { testClass.throwsException() }
        future.get() // wait until is done

        then: "proxy still works"
        testClass.numTimesCalled == 3 + 3 // initial call + 2 retries
        notThrown HibernateOptimisticLockingFailureException // because in a separate thread
        stdErr.toString().contains("addSessionAndTransaction")
        stdErr.toString().contains("uncaught exception")
        stdErr.toString().contains("HibernateOptimisticLockingFailureException")

        cleanup:
        TestUtils.restoreAllStreams()
    }
}
