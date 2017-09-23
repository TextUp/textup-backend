package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultFactorySpec extends Specification {

	@Shared
	MessageSource messageSource = new StaticMessageSource()

	static doWithSpring = {
        resultFactory(ResultFactory)
    }
    def setup() {
        ResultFactory fac = getResultFactory()
        fac.messageSource = messageSource
    }

    protected ResultFactory getResultFactory() {
        grailsApplication.mainContext.getBean("resultFactory")
    }

    void "test getting messages from codes"() {
    	given:
    	ResultFactory fac = getResultFactory()
    	String code = "but I do exist!!!"
    	messageSource.addMessage(code, Locale.default, code)

    	expect:
    	fac.getMessage("i do not exist", []) == ""
    	fac.getMessage(code, []) == code
    }

    void "test success"() {
    	given:
    	ResultFactory fac = getResultFactory()
    	Location loc1 = new Location(address:"Testing Address", lat:0G, lon:0G)
    	loc1.save(flush:true, failOnError:true)

    	when: "without specifying payload"
    	Result<Location> res = fac.<Location>success()

    	then:
    	res.success == true
    	res.status == ResultStatus.NO_CONTENT
    	res.payload == null
    	res.errorMessages.isEmpty() == true

    	when: "with specified payload"
    	res = fac.<Location>success(loc1)

    	then:
    	res.success == true
    	res.errorMessages.isEmpty() == true
    	res.status == ResultStatus.OK
    	res.payload.id == loc1.id
    }

    void "test rolling back transaction on failure"() {
    	given:
    	ResultFactory fac = getResultFactory()
    	int baseline = Location.count()

    	when: "is failure"
    	Location loc1
    	Result<Location> res
    	Organization.withTransaction {
    		loc1 = new Location(address:"i am the address", lat:0G, lon:0G)
    		loc1.save()
    		assert loc1.hasErrors() == false
    		res = fac.failWithValidationErrors(loc1.errors)
    	}

    	then: "not persisted even if location is valid"
    	Location.count() == baseline
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages.isEmpty() == true
    	res.payload == null

    	when: "not failure"
    	Organization.withTransaction {
    		loc1 = new Location(address:"i am the address", lat:0G, lon:0G)
    		loc1.save()
    		assert loc1.hasErrors() == false
    		res = fac.success(loc1)
    	}

    	then: "is persisted"
    	Location.count() == baseline + 1
    	res.success == true
    	res.status == ResultStatus.OK
    	res.errorMessages.isEmpty() == true
    	res.payload.id == loc1.id
    }
}
