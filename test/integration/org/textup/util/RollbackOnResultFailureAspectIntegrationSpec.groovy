package org.textup.util

import grails.test.runtime.FreshRuntime
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.textup.*
import spock.lang.*

class RollbackOnResultFailureAspectIntegrationSpec extends Specification {

    // very important to set this to be false or else this ENTIRE TEST will run within
    // ONE TRANSACTION and our assertions that are based on rolling back the transaction will fail
    static transactional = false

    GrailsApplication grailsApplication

    def setup() {
        // in order for the Transactional annotation on the test class bean to be propertly
        // configured, we cannot manually instantiate the bean. Instead, we must inject the bean
        // via dependency injection or else we will get NPEs when trying to call any method
        // See https://blog.10ne.org/2014/01/08/registering-new-spring-beans-in-grails-during-runtime/
        GenericBeanDefinition beanDef = new GenericBeanDefinition(beanClass: RollbackTestClass,
            autowireMode: AbstractBeanDefinition.AUTOWIRE_BY_NAME)
        grailsApplication.mainContext.registerBeanDefinition('rollbackTestClass', beanDef)
    }

    @FreshRuntime
    void "test without annotation"() {
        given:
        int lBaseline = Location.count()
        RollbackTestClass testClass = getTestClass()

        when: "notAnnotated with result failure"
        testClass.notAnnotated {
            buildLocation()
            new Result(status: ResultStatus.BAD_REQUEST)
        }

        then: "NOT rolled back"
        Location.count() == lBaseline + 1
    }

    @FreshRuntime
    void "test with annotation and correct return type"() {
        given:
        int lBaseline = Location.count()
        RollbackTestClass testClass = getTestClass()

        when: "annotated and correct return type with result failure"
        testClass.correctReturn {
            buildLocation()
            new Result(status: ResultStatus.BAD_REQUEST)
        }

        then: "DID roll back"
        Location.count() == lBaseline
    }

    @FreshRuntime
    void "test with annotation but incorrect return type"() {
        given:
        int lBaseline = Location.count()
        RollbackTestClass testClass = getTestClass()

        when: "annotated and wrong return type"
        testClass.incorrectReturn {
            buildLocation()
            false
        }

        then: "NOT rolled back"
        Location.count() == lBaseline + 1
    }

    // Test support + helper methods
    // -----------------------------

    protected RollbackTestClass getTestClass() {
        grailsApplication.mainContext.getBean(RollbackTestClass)
    }

    protected Location buildLocation() {
        new Location(address: "hello", lat: 0G, lon: 0G).save()
    }
}
