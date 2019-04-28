package org.textup

import grails.test.runtime.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Unroll
class OutgoingMessageServiceIntegrationSpec extends Specification {

    // very important to set this to be false or else this ENTIRE TEST will run within
    // ONE TRANSACTION and our assertions that are based on being able to re-fetch items from ids will fail
    static transactional = false

    static final int NUM_CONTACTS = ValidationUtils.MAX_NUM_TEXT_RECIPIENTS
    static final int NUM_TAGS = 20

    CallService callService
    GrailsApplication grailsApplication
    OutgoingMessageService outgoingMessageService
    TextService textService

    AtomicInteger textSendInvokeCount
    AtomicInteger callStartInvokeCount
    Phone thisPhone

    def setup() {
        // need to return a valid url for calls
        IOCUtils.metaClass."static".getLinkGenerator = { ->
            [link: { Map m -> "https://www.example.com" }] as LinkGenerator
        }
        // setting up textService
        textService = grailsApplication.mainContext.getBean("textService")
        textService.metaClass.invokeMethod = { String name, Object args ->
            if (name == "send") {
                textSendInvokeCount.getAndIncrement()
            }
            delegate.metaClass
                .getMetaMethod(name, *args)
                .invoke(delegate, *args)
        }
        // setting up callService
        callService = grailsApplication.mainContext.getBean("callService")
        callService.metaClass.invokeMethod = { String name, Object args ->
            if (name == "start") {
                callStartInvokeCount.getAndIncrement()
            }
            delegate.metaClass
                .getMetaMethod(name, *args)
                .invoke(delegate, *args)
        }
    }

    def cleanup() {
        // to avoid duplicate errors because we need to use fixed Twilio test numbers
        thisPhone.numberAsString = TestUtils.randPhoneNumberString()
        thisPhone.save(flush: true, failOnError: true)
    }

    @DirtiesRuntime
    void "test sending out high volume of outgoing #type for one TextUp number"() {
        given:
        // globals
        thisPhone = TestUtils.buildActiveStaffPhone()
        thisPhone.number = PhoneNumber.create(sandboxFromNum)
        textSendInvokeCount = new AtomicInteger(0)
        callStartInvokeCount = new AtomicInteger(0)
        // phone records
        HashSet allNumbers = new HashSet()
        List allContacts = []
        NUM_CONTACTS.times {
            IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(thisPhone)
            allNumbers.addAll(ipr1.numbers*.e164PhoneNumber)
            allContacts << ipr1
        }
        List allTags = []
        NUM_TAGS.collect {
            GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(thisPhone)
            allContacts[0..(TestUtils.randIntegerUpTo(NUM_CONTACTS - 1, true))].each { ipr1 ->
                gpr1.members.addToPhoneRecords(ipr1)
                allNumbers.addAll(ipr1.numbers*.e164PhoneNumber)
            }
            allTags << gpr1
        }
        // baselines
        int tBaseline = RecordText.count()
        int cBaseline = RecordCall.count()
        int iBaseline = RecordItem.count()
        // packaging for service
        Recipients recip1 = Recipients
            .tryCreate(allContacts + allTags, thisPhone.language, ValidationUtils.MAX_NUM_TEXT_RECIPIENTS * 2)
            .payload
        TempRecordItem temp1 = TestUtils.buildTempRecordItem()

        when: "sending out to many contacts and tags without staff (for authorship)"
        Tuple itemsAndFuture = outgoingMessageService
            .tryStart(type, recip1, temp1, null)
            .payload
        List rItems = itemsAndFuture.first
        Future<?> fut1 = itemsAndFuture.second

        then: "successful, no duplicates because all shared contacts resolved into contacts"
        // double check this number with overall items counter to confirm only appropriate type were added
        RecordItem.count() == iBaseline + rItems.size()

        when: "we wait for the future to finish processing + send messages"
        fut1.get()

        then:
        if (type == RecordItemType.TEXT) {
            // no calls made
            assert callStartInvokeCount.intValue() == 0
            assert textSendInvokeCount.intValue() == allContacts.size()
            // we are supposed to build a record item for each contactable and tag's record
            // textSendInvokeCount is the number of unique contactables and
            // tags is the number of tags so the sum should be all the results in our group
            assert textSendInvokeCount.intValue() + allTags.size() == rItems.size()
            // each success is a new text stored in either a contactable or tag record
            assert RecordText.count() == tBaseline + rItems.size()
        }
        else {
            // no texts made
            assert textSendInvokeCount.intValue() == 0
            assert callStartInvokeCount.intValue() == allContacts.size()
            // we are supposed to build a record item for each contactable and tag's record
            // callStartInvokeCount is the number of unique contactables and
            // tags is the number of tags so the sum should be all the results in our group
            assert callStartInvokeCount.intValue() + allTags.size() == rItems.size()
            // each success is a new text stored in either a contactable or tag record
            assert RecordCall.count() == cBaseline + rItems.size()
        }

        where:
        type                | sandboxFromNum
        RecordItemType.CALL | TestConstants.TEST_CALL_FROM_VALID
        RecordItemType.TEXT | TestConstants.TEST_SMS_FROM_VALID
    }
}
