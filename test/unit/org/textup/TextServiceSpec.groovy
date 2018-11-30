package org.textup

import com.twilio.Twilio
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.context.MessageSource
import org.textup.util.TestUtils
import org.textup.validator.*
import spock.lang.*

@TestFor(TextService)
class TextServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getLinkGenerator = { -> TestUtils.mockLinkGenerator() }
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        def twilioConfig = grailsApplication.config.textup.apiKeys.twilio
        Twilio.init(twilioConfig.sid, twilioConfig.authToken)
    	service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    void "test send"() {
    	when: "send to no recipients"
    	PhoneNumber fromNum = new PhoneNumber(number:TestConstants.TEST_SMS_FROM_VALID)
    	assert fromNum.validate()
    	String msg = "hello there!!"
    	Result res = service.send(fromNum, [], msg)

    	then: "no numbers were attempted since no receipients so we get fallback error msg"
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0] == "textService.text.noNumbers"

        when: "try to text an invalid number"
        PhoneNumber invalidToNum1 = new PhoneNumber(number:TestConstants.TEST_SMS_TO_NOT_VALID),
            invalidToNum2 = new PhoneNumber(number:TestConstants.TEST_SMS_TO_BLACKLISTED)
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
        res.payload.numSegments > 0
    }

    void "test send with media"() {
        given: "info for outgoing text"
        PhoneNumber fromNum = new PhoneNumber(number:TestConstants.TEST_SMS_FROM_VALID)
        assert fromNum.validate()
        PhoneNumber toNum1 = new PhoneNumber(number:"+16268943239"),
            toNum2 = new PhoneNumber(number:"+16260943239")
        assert toNum1.validate() && toNum2.validate()
        String msg = "hello there!!"

        and: "list of media elements"
        Collection<URI> mediaLinks = []
        8.times { mediaLinks << new URI("https://www.example.com/${TestUtils.randString()}") }

        when:
        Result res = service.send(fromNum, [toNum1, toNum2], msg, mediaLinks)

        then: "stop on first number + handle media normally"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TempRecordReceipt
        res.payload.contactNumberAsString == toNum1.number
        res.payload.apiId != null
        res.payload.numSegments > 0
    }
}
