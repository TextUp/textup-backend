package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.type.LogLevel
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultSpec extends Specification {

	protected Location buildLocation() {
		Location loc1 = new Location(address:"Testing Address", lat:0G, lon:0G)
    	loc1.save(flush:true, failOnError:true)
	}

    void "test static creators"() {
    	given: "a valid location"
    	Location loc1 = buildLocation()

    	when: "creating success"
    	Result<Location> res = Result.<Location>createSuccess(loc1, ResultStatus.CREATED)

    	then:
    	res.success == true
    	res.status == ResultStatus.CREATED
    	res.errorMessages.isEmpty() == true
    	res.payload.id == loc1.id

    	when: "creating error"
    	String msg = "I am an error"
    	res = Result.<Location>createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)

    	then:
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages.size() == 1
    	res.errorMessages[0] == msg
    	res.payload == null
    }

    void "test chaining results"() {
    	given: "one success and one failure result"
    	String msg = "I am an error"
    	Result<Location> failRes = Result.<Location>createError([msg], ResultStatus.UNPROCESSABLE_ENTITY)
    	Result<Location> successRes = Result.<Location>createSuccess(buildLocation(), ResultStatus.CREATED)

    	when: "for failed result"
    	int numTimesFailed = 0

    	failRes.thenEnd({ numTimesFailed-- }, { numTimesFailed++ })
    	Result res = failRes.then({ numTimesFailed--; successRes }, { numTimesFailed++; failRes; })

    	then: "appropriate action is called"
    	numTimesFailed == 2
    	res.success == failRes.success
    	res.status == failRes.status
    	res.errorMessages[0] == failRes.errorMessages[0]

    	when: "for successful result"
    	numTimesFailed = 0

    	successRes.thenEnd({ numTimesFailed-- }, { numTimesFailed++ })
    	res = successRes.then({ numTimesFailed--; successRes }, { numTimesFailed++; failRes; })

    	then: "appropriate action is called"
    	numTimesFailed == -2
    	res.success == successRes.success
    	res.status == successRes.status
    	res.errorMessages.isEmpty() == true
    	res.payload.id == successRes.payload.id
    }
}
