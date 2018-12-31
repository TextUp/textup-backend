package org.textup

import com.twilio.exception.ApiException
import com.twilio.rest.api.v2010.account.Call
import com.twilio.rest.api.v2010.account.CallCreator
import com.twilio.rest.api.v2010.account.CallUpdater
import com.twilio.Twilio
import com.twilio.type.PhoneNumber as TwilioPhoneNumber
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.context.MessageSource
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*
import spock.util.mop.ConfineMetaClassChanges

@Domain([CustomAccountDetails, Record, RecordItem, RecordText, RecordCall, RecordItemReceipt,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
@TestFor(CallService)
class CallServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        IOCUtils.metaClass."static".getLinkGenerator = { -> TestUtils.mockLinkGeneratorWithDomain() }
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
        TestUtils.mockJsonToString()
    }

    // Helpers
    // -------

    // see global mock cleanup bug: https://github.com/spockframework/spock/issues/445
    @ConfineMetaClassChanges([Call])
    void "test building call creator"() {
        given:
        BasePhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        TwilioPhoneNumber apiTo = toNum.toApiPhoneNumber()
        TwilioPhoneNumber apiFrom = fromNum.toApiPhoneNumber()
        String customAccountId = TestUtils.randString()
        String pickupKey = TestUtils.randString()
        String pickupVal = TestUtils.randString()
        Map afterPickup = [(pickupKey): pickupVal]

        CallCreator mockCreator = GroovyMock()
        GroovyMock(Call, global: true)

        when: "no custom id"
        CallCreator retVal = service.callCreator(fromNum, toNum, afterPickup, null)

        then:
        1 * Call.creator(apiTo, apiFrom,
            { it.toString().contains(pickupKey) && it.toString().contains(pickupVal) }) >> mockCreator
        retVal == mockCreator

        when: "has custom id"
        retVal = service.callCreator(fromNum, toNum, afterPickup, customAccountId)

        then:
        1 * Call.creator(customAccountId, apiTo, apiFrom,
            { it.toString().contains(pickupKey) && it.toString().contains(pickupVal) }) >> mockCreator
        retVal == mockCreator
    }

    // see global mock cleanup bug: https://github.com/spockframework/spock/issues/445
    @ConfineMetaClassChanges([Call])
    void "test building call updater"() {
        given:
        String callId = TestUtils.randString()
        String customAccountId = TestUtils.randString()

        CallUpdater mockUpdater = GroovyMock()
        GroovyMock(Call, global: true)

        when: "no custom id"
        CallUpdater retVal = service.callUpdater(callId, null)

        then:
        1 * Call.updater(callId) >> mockUpdater
        retVal == mockUpdater

        when: "has custom id"
        retVal = service.callUpdater(callId, customAccountId)

        then:
        1 * Call.updater(customAccountId, callId) >> mockUpdater
        retVal == mockUpdater
    }

    void "test executing call"() {
        given:
        CallCreator creator = GroovyMock()
        String callback = TestUtils.randString()

        when:
        CallService.Outcome retVal = service.executeCall(creator, callback)

        then:
        1 * creator.setStatusCallback(callback) >> creator
        1 * creator.create() >> GroovyMock(Call) // all getters are final
        retVal instanceof CallService.Outcome
    }

    void "test do call helper errors"() {
        given:
        BasePhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        String customAccountId = TestUtils.randString()
        String callback = TestUtils.randString()
        Map afterPickup = [(TestUtils.randString()): TestUtils.randString()]

        when: "missing from or to"
        Result<TempRecordReceipt> res = service
            .doCall(null, null, afterPickup, callback, customAccountId)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages == ["callService.doCall.missingInfo"]

        when: "non ApiException is thrown"
        MockedMethod callCreator = TestUtils.mock(service, "callCreator") {
            throw new IllegalArgumentException(TestUtils.randString())
        }
        res = service.doCall(fromNum, toNum, afterPickup, callback, customAccountId)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when: "ApiException is thrown"
        callCreator.restore()
        callCreator = TestUtils.mock(service, "callCreator") {
            throw new ApiException(TestUtils.randString())
        }
        res = service.doCall(fromNum, toNum, afterPickup, callback, customAccountId)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        cleanup:
        callCreator.restore()
    }

    void "test do call helper success"() {
        given:
        BasePhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        String customAccountId = TestUtils.randString()
        String callback = TestUtils.randString()
        Map afterPickup = [(TestUtils.randString()): TestUtils.randString()]

        String callId = TestUtils.randString()
        CallCreator creator = Mock()
        MockedMethod callCreator = TestUtils.mock(service, "callCreator") { creator }
        MockedMethod executeCall = TestUtils.mock(service, "executeCall") {
            new CallService.Outcome(sid: callId)
        }

        when:
        Result<TempRecordReceipt> res = service.doCall(fromNum, toNum, afterPickup, callback, customAccountId)

        then:
        callCreator.callCount == 1
        callCreator.callArguments[0] == [fromNum, toNum, afterPickup, customAccountId]
        executeCall.callCount == 1
        executeCall.callArguments[0] == [creator, callback]
        res.status == ResultStatus.OK
        res.payload instanceof TempRecordReceipt
        res.payload.contactNumber.number == toNum.number
        res.payload.apiId == callId

        cleanup:
        callCreator.restore()
        executeCall.restore()
    }

    void "test building callback url"() {
        given:
        List<String> remaining = [TestUtils.randPhoneNumber(), TestUtils.randPhoneNumber()]
        String afterPickupJson = TestUtils.randString()

        when: "has remaining"
        String callback = service.buildCallbackUrl(remaining, afterPickupJson)

        then:
        remaining.every { callback.contains(it) }
        callback.contains(afterPickupJson)

        when: "no remaining"
        callback = service.buildCallbackUrl(null, afterPickupJson)

        then:
        callback.contains("remaining") == false
        callback.contains(afterPickupJson) == false
    }

    // Actions
    // -------

    void "test interrupting existing call"() {
        given:
        String customAccountId = TestUtils.randString()
        String callId = TestUtils.randString()
        String pickupKey = TestUtils.randString()
        String pickupVal = TestUtils.randString()

        CallUpdater updater = GroovyMock()
        MockedMethod callUpdater = TestUtils.mock(service, "callUpdater") {
            throw new IllegalArgumentException(TestUtils.randString())
        }

        when:  "has error"
        Result<Void> res = service.interrupt(callId, [:], customAccountId)

        then: "errors gracefully handled"
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when: "no error"
        callUpdater.restore()
        callUpdater = TestUtils.mock(service, "callUpdater") { updater }
        res = service.interrupt(callId, [(pickupKey): pickupVal], customAccountId)

        then:
        1 * updater.setUrl({ it.toString().contains(pickupKey) &&
            it.toString().contains(pickupVal) }) >> updater
        1 * updater.setStatusCallback({ it.toString().contains(Constants.CALLBACK_STATUS) }) >> updater
        1 * updater.update()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        callUpdater.restore()
    }

    void "test retrying call"() {
        given:
        BasePhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        String apiId = TestUtils.randString()
        String customAccountId = TestUtils.randString()
        Map afterPickup = [(TestUtils.randString()): TestUtils.randString()]

        TempRecordReceipt tempRpt1 = TestUtils.buildTempReceipt()
        RecordItem mockItem = Mock()
        MockedMethod start = TestUtils.mock(service, "start") { new Result(payload: tempRpt1) }
        MockedMethod findEveryByApiId = TestUtils.mock(RecordItem, "findEveryByApiId") { [mockItem] }

        when:
        Result<TempRecordReceipt> res = service.retry(fromNum, [toNum], apiId, afterPickup, customAccountId)

        then:
        start.callCount == 1
        start.callArguments[0] == [fromNum, [toNum], afterPickup, customAccountId]
        findEveryByApiId.callCount == 1
        findEveryByApiId.callArguments[0] == [apiId]
        1 * mockItem.addReceipt(tempRpt1)
        1 * mockItem.save()
        res.status == ResultStatus.OK
        res.payload == tempRpt1

        cleanup:
        start.restore()
        findEveryByApiId.restore()
    }

    void "test starting call errors"() {
        given:
        BasePhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        Map afterPickup = [(TestUtils.randString()): TestUtils.randString()]
        String customAccountId = TestUtils.randString()

        String errorMsg = TestUtils.randString()
        MockedMethod doCall = TestUtils.mock(service, "doCall") {
            new Result(status: ResultStatus.INTERNAL_SERVER_ERROR,
                errorMessages: [errorMsg])
        }

        when: "no numbers"
        Result<TempRecordReceipt> res = service.start(fromNum, null, afterPickup, customAccountId)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "callService.start.missingInfoOrAllFailed"

        when: "all numbers immediately failed"
        res = service.start(fromNum, [toNum], afterPickup, customAccountId)

        then:
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == errorMsg

        cleanup:
        doCall.restore()
    }

    void "test starting call successfully"() {
        given:
        BasePhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum1 = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum2 = new PhoneNumber(number: TestUtils.randPhoneNumber())
        BasePhoneNumber toNum3 = new PhoneNumber(number: TestUtils.randPhoneNumber())
        Map afterPickup = [(TestUtils.randString()): TestUtils.randString()]
        String customAccountId = TestUtils.randString()

        TempRecordReceipt tempRpt = TestUtils.buildTempReceipt()
        MockedMethod doCall = TestUtils.mock(service, "doCall") { new Result(payload: tempRpt) }

        when: "one number"
        Result<TempRecordReceipt> res = service.start(fromNum, [toNum1], afterPickup, customAccountId)

        then:
        doCall.callCount == 1
        doCall.callArguments[0][0] == fromNum
        doCall.callArguments[0][1] == toNum1
        doCall.callArguments[0][2] == afterPickup
        doCall.callArguments[0][3].contains("remaining") == false
        doCall.callArguments[0][3].contains("afterPickup") == false
        doCall.callArguments[0][4] == customAccountId
        res.status == ResultStatus.OK
        res.payload == tempRpt

        when: "multiple numbers"
        res = service.start(fromNum, [toNum2, toNum3], afterPickup, customAccountId)

        then: "stop on first number that works"
        doCall.callCount == 2
        doCall.callArguments[1][0] == fromNum
        doCall.callArguments[1][1] == toNum2
        doCall.callArguments[1][2] == afterPickup
        doCall.callArguments[1][3].contains("remaining") == true
        doCall.callArguments[1][3].contains(toNum3.e164PhoneNumber) == true
        doCall.callArguments[1][3].contains("afterPickup") == true
        doCall.callArguments[1][4] == customAccountId
        res.status == ResultStatus.OK
        res.payload == tempRpt

        cleanup:
        doCall.restore()
    }

    void "test starting a call to one or more numbers"() {
        given:
        Map twilioTestConfig = grailsApplication.config.textup.apiKeys.twilio
        Twilio.init(twilioTestConfig.sid, twilioTestConfig.authToken)

        PhoneNumber invalidFrom1 = new PhoneNumber(number:TestConstants.TEST_CALL_FROM_NOT_VALID)
        PhoneNumber fromNum1 = new PhoneNumber(number:TestConstants.TEST_CALL_FROM_VALID),
            toNum1 = new PhoneNumber(number: TestUtils.randPhoneNumber()),
            toNum2 = new PhoneNumber(number: TestUtils.randPhoneNumber()),
            invalidNum1 = new PhoneNumber(number:TestConstants.TEST_CALL_TO_NOT_VALID)
        assert [invalidFrom1, fromNum1, toNum1, toNum2, invalidNum1].each { it.validate() }

        when: "we try to call with an invalid 'from' number"
        Result<TempRecordReceipt> res = service.start(invalidFrom1, [toNum1], [:], null)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "we start the call with an invalid 'to' number"
        res = service.start(fromNum1, [invalidNum1], [:], null)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "we start a call with one 'to' number"
        res = service.start(fromNum1, [toNum1], [:], null)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TempRecordReceipt
        res.payload.apiId != null
        res.payload.contactNumberAsString == toNum1.number

        when: "we start a call with multiple 'to' numbers where first is invalid"
        res = service.start(fromNum1, [invalidNum1, toNum1, toNum2], [:], null)

        then: "first invalid number is ignored"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof TempRecordReceipt
        res.payload.apiId != null
        res.payload.contactNumberAsString == toNum1.number
    }
}
