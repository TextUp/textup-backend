package org.textup.util

import com.twilio.rest.api.v2010.account.Message
import com.twilio.rest.api.v2010.account.MessageCreator
import com.twilio.Twilio
import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*
import spock.util.mop.ConfineMetaClassChanges

@TestFor(TextService)
class TextServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getLinkGenerator = { -> TestUtils.mockLinkGenerator() }
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    // see global mock cleanup bug: https://github.com/spockframework/spock/issues/445
    @ConfineMetaClassChanges([Message])
    void "test building message creator"() {
        given:
        BasePhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        BasePhoneNumber toNum = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        TwilioPhoneNumber apiTo = toNum.toApiPhoneNumber()
        TwilioPhoneNumber apiFrom = fromNum.toApiPhoneNumber()
        String message = TestUtils.randString()
        String customAccountId = TestUtils.randString()

        MessageCreator mockMsgCreator = GroovyMock()
        GroovyMock(Message, global: true)

        when: "no custom account id"
        MessageCreator retVal = service.messageCreator(fromNum, toNum, message, null)

        then:
        1 * Message.creator(apiTo, apiFrom, message) >> mockMsgCreator
        retVal == mockMsgCreator

        when: "has custom account id"
        retVal = service.messageCreator(fromNum, toNum, message, customAccountId)

        then:
        1 * Message.creator(customAccountId, apiTo, apiFrom, message) >> mockMsgCreator
        retVal == mockMsgCreator
    }

    void "test send errors + using master account credentials"() {
        given:
        Map twilioConfig = grailsApplication.config.textup.apiKeys.twilio
        Twilio.init(twilioConfig.sid, twilioConfig.authToken)

        and:
        String msg = TestUtils.randString()
        String customAccountId = null

        PhoneNumber fromNum = new PhoneNumber(number:TestConstants.TEST_SMS_FROM_VALID)
        assert fromNum.validate()
        PhoneNumber invalidToNum1 = new PhoneNumber(number:TestConstants.TEST_SMS_TO_NOT_VALID),
            invalidToNum2 = new PhoneNumber(number:TestConstants.TEST_SMS_TO_BLACKLISTED)
        assert invalidToNum1.validate() && invalidToNum2.validate()

    	when: "send to no recipients"
    	Result res = service.send(fromNum, [], msg, customAccountId)

    	then: "no numbers were attempted since no receipients so we get fallback error msg"
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0] == "textService.text.noNumbers"

        when: "try to text an invalid number"
        res = service.send(fromNum, [invalidToNum1, invalidToNum2], msg, customAccountId)

        then: "get the custom error message"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 2
        res.errorMessages.every { it != "textService.text.noNumbers" }
    }

    void "test try text"() {
        given:
        String msg = TestUtils.randString()
        String customAccountId = TestUtils.randString()
        Collection<URI> mediaLinks = [TestUtils.randUrl(), TestUtils.randUrl()]

        PhoneNumber fromNum = new PhoneNumber(number: TestConstants.TEST_SMS_FROM_VALID)
        assert fromNum.validate()
        PhoneNumber toNum1 = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        assert toNum1.validate()

        MessageCreator mockMsgCreator = GroovyMock()
        MockedMethod messageCreator = MockedMethod.create(service, "messageCreator") { mockMsgCreator }

        when: "no media"
        Result res = service.tryText(fromNum, toNum1, msg, customAccountId, [])

        then:
        messageCreator.callCount == 1
        messageCreator.allArgs[0] == [fromNum, toNum1, msg, customAccountId]
        1 * mockMsgCreator.setStatusCallback(*_) >> mockMsgCreator
        1 * mockMsgCreator.setMediaUrl([]) >> mockMsgCreator
        1 * mockMsgCreator.create() >> GroovyMock(Message) // all getters are final so can't mock
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TextService.Outcome

        when: "has media"
        res = service.tryText(fromNum, toNum1, msg, customAccountId, mediaLinks)

        then:
        messageCreator.callCount == 2
        messageCreator.allArgs[1] == [fromNum, toNum1, msg, customAccountId]
        1 * mockMsgCreator.setStatusCallback(*_) >> mockMsgCreator
        1 * mockMsgCreator.setMediaUrl(mediaLinks) >> mockMsgCreator
        1 * mockMsgCreator.create() >> GroovyMock(Message) // all getters are final so can't mock
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TextService.Outcome

        cleanup:
        messageCreator.restore()
    }

    void "test successfully sending for multiple numbers"() {
        given:
        PhoneNumber fromNum = new PhoneNumber(number: TestConstants.TEST_SMS_FROM_VALID)
        assert fromNum.validate()
        String msg = TestUtils.randString()
        String customAccountId = TestUtils.randString()
        Collection<URI> mediaLinks = [TestUtils.randUrl(), TestUtils.randUrl()]

        PhoneNumber toNum1 = new PhoneNumber(number: TestUtils.randPhoneNumberString()),
            toNum2 = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        assert toNum1.validate() && toNum2.validate()

        TextService.Outcome mockMsgResult = GroovyMock()
        MockedMethod tryText = MockedMethod.create(service, "tryText") {
            new Result(payload: mockMsgResult)
        }

        and: "message outcome values"
        String apiId = TestUtils.randString()
        Integer numSegments = TestUtils.randIntegerUpTo(10, true)

        when: "stop on first success"
        Result res = service.send(fromNum, [toNum1, toNum2], msg, customAccountId, mediaLinks)

        then:
        tryText.callCount == 1
        tryText.allArgs[0] == [fromNum, toNum1, msg, customAccountId, mediaLinks]
        1 * mockMsgResult.getSid() >> apiId
        1 * mockMsgResult.getNumSegments() >> numSegments

        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TempRecordReceipt
        res.payload.contactNumberAsString == toNum1.number
        res.payload.apiId == apiId
        res.payload.numSegments == numSegments

        cleanup:
        tryText.restore()
    }
}
