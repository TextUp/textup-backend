package org.textup

import com.twilio.Twilio
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.context.MessageSource
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import spock.lang.Shared
import spock.lang.Specification

@Domain([Organization, Location])
@TestMixin(HibernateTestMixin)
@TestFor(TextService)
class TextServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        def twilioTestConfig = grailsApplication.config.textup.apiKeys.twilio
        Twilio.init(twilioTestConfig.sid, twilioTestConfig.authToken)

    	service.resultFactory = grailsApplication.mainContext.getBean("resultFactory")
    	service.resultFactory.messageSource = [getMessage:{ String c, Object[] p, Locale l ->
            c
        }] as MessageSource
        service.grailsLinkGenerator = [
            link: { Map params -> "http://www.example.com" }
        ] as LinkGenerator
    }

    void "test send"() {
    	when: "send to no recipients"
    	PhoneNumber fromNum = new PhoneNumber(number:Constants.TEST_SMS_FROM_VALID)
    	assert fromNum.validate()
    	String msg = "hello there!!"
    	Result res = service.send(fromNum, [], msg)

    	then: "no numbers were attempted since no receipients so we get fallback error msg"
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0] == "textService.text.noNumbers"

        when: "try to text an invalid number"
        PhoneNumber invalidToNum1 = new PhoneNumber(number:Constants.TEST_SMS_TO_NOT_VALID),
            invalidToNum2 = new PhoneNumber(number:Constants.TEST_SMS_TO_BLACKLISTED)
        assert invalidToNum1.validate() && invalidToNum2.validate()
        res = service.send(fromNum, [invalidToNum1, invalidToNum2], msg)

        then: "get the custom error message"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 2
        res.errorMessages.every { it != "textService.text.noNumbers" }

    	when: "stop on first success"
    	PhoneNumber toNum1 = new PhoneNumber(number:"+16268943239"),
    		toNum2 = new PhoneNumber(number:"+16260943239")
		assert toNum1.validate() && toNum2.validate()
    	res = service.send(fromNum, [toNum1, toNum2], msg)

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload instanceof TempRecordReceipt
    	res.payload.contactNumberAsString == toNum1.number
        res.payload.apiId != null
    }
}
