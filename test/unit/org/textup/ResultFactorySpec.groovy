package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.context.support.StaticMessageSource
import org.textup.util.TestHelpers
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
class ResultFactorySpec extends Specification {

	@Shared
	MessageSource staticMessageSource = new StaticMessageSource()

    ResultFactory resultFactory

    def setup() {
        resultFactory = new ResultFactory()
        resultFactory.messageSource = staticMessageSource
    }

    void "test getting messages from codes"() {
    	when: "code does exist"
    	String code = "but I do exist!!!"
    	staticMessageSource.addMessage(code, Locale.default, code)
        String result = resultFactory.getMessage(code, [])

        then:
        result == code

        when: "code doesn't exist"
        result = resultFactory.getMessage("i do not exist", [])

    	then:
    	result == ""
    }

    void "test success"() {
    	given:
    	Location loc1 = new Location(address:"Testing Address", lat:0G, lon:0G)
    	loc1.save(flush:true, failOnError:true)

    	when: "without specifying payload"
    	Result<Location> res = resultFactory.<Location>success()

    	then:
    	res.success == true
    	res.status == ResultStatus.NO_CONTENT
    	res.payload == null
    	res.errorMessages.isEmpty() == true

    	when: "with specified payload"
    	res = resultFactory.<Location>success(loc1)

    	then:
    	res.success == true
    	res.errorMessages.isEmpty() == true
    	res.status == ResultStatus.OK
    	res.payload.id == loc1.id
    }
}
