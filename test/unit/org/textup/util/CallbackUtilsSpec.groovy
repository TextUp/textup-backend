package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class CallbackUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test if should update receipt status"() {
        expect:
        CallbackUtils.shouldUpdateStatus(null, null) == false
        CallbackUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, null) == false
        CallbackUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, ReceiptStatus.PENDING) == false

        and:
        CallbackUtils.shouldUpdateStatus(null, ReceiptStatus.SUCCESS) == true
        CallbackUtils.shouldUpdateStatus(ReceiptStatus.PENDING, ReceiptStatus.SUCCESS) == true
        CallbackUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, ReceiptStatus.BUSY) == true
        CallbackUtils.shouldUpdateStatus(ReceiptStatus.SUCCESS, ReceiptStatus.FAILED) == true
    }

    void "test if should update receipt call duration"() {
        expect:
        CallbackUtils.shouldUpdateDuration(null, null) == false
        CallbackUtils.shouldUpdateDuration(88, null) == false
        CallbackUtils.shouldUpdateDuration(88, 88) == false

        and:
        CallbackUtils.shouldUpdateDuration(null, 88) == true
        CallbackUtils.shouldUpdateDuration(88, 21) == true
    }

    void "test normalizing session's number from incoming params"() {
        given:
        PhoneNumber fromNum = TestUtils.randPhoneNumber()
        PhoneNumber toNum = TestUtils.randPhoneNumber()
        PhoneNumber processedNum = TestUtils.randPhoneNumber()
        MockedMethod tryExtractScreenIncomingFrom = TestUtils.mock(CallTwiml, "tryExtractScreenIncomingFrom") {
            Result.createSuccess(processedNum)
        }

        when: "missing handle"
        Result res = CallbackUtils.tryGetNumberForSession(fromNum, toNum, new TypeMap())

        then:
        tryExtractScreenIncomingFrom.callCount == 0
        res.status == ResultStatus.OK
        res.payload == fromNum

        when: "session is `to` number"
        res = CallbackUtils.tryGetNumberForSession(fromNum, toNum,
            TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.VOICEMAIL_GREETING_PLAY))

        then:
        tryExtractScreenIncomingFrom.callCount == 0
        res.status == ResultStatus.OK
        res.payload == toNum

        when: "additional processing for SCREEN_INCOMING"
        res = CallbackUtils.tryGetNumberForSession(fromNum, toNum,
            TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.SCREEN_INCOMING))

        then:
        tryExtractScreenIncomingFrom.callCount == 1
        res.status == ResultStatus.OK
        res.payload == processedNum

        when: "session is `from` number"
        res = CallbackUtils.tryGetNumberForSession(fromNum, toNum,
            TypeMap.create((CallbackUtils.PARAM_HANDLE): "anything else"))

        then:
        tryExtractScreenIncomingFrom.callCount == 1
        res.status == ResultStatus.OK
        res.payload == fromNum

        cleanup:
        tryExtractScreenIncomingFrom.restore()
    }

    void "test normalizing phone's number from incoming params"() {
        given:
        BasePhoneNumber fromNum = TestUtils.randPhoneNumber()
        BasePhoneNumber toNum = TestUtils.randPhoneNumber()

        when: "missing handle"
        BasePhoneNumber retNum = CallbackUtils.numberForPhone(fromNum, toNum, new TypeMap())

        then:
        retNum == toNum

        when: "phone is `from` number"
        retNum = CallbackUtils.numberForPhone(fromNum, toNum,
            TypeMap.create((CallbackUtils.PARAM_HANDLE): CallResponse.SCREEN_INCOMING))

        then:
        retNum == fromNum

        when: "phone is `to` number"
        retNum = CallbackUtils.numberForPhone(fromNum, toNum,
            TypeMap.create((CallbackUtils.PARAM_HANDLE): "anything else"))

        then:
        retNum == toNum
    }
}
