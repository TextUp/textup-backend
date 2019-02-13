package org.textup

import grails.test.runtime.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Unroll

// TODO

@Unroll
class OutgoingMessageServiceIntegrationSpec extends CustomSpec {

    // // very important to set this to be false or else this ENTIRE TEST will run within
    // // ONE TRANSACTION and our assertions that are based on being able to re-fetch items from ids will fail
    // static transactional = false

    // final int NUM_TAGS = 20

    // CallService callService
    // GrailsApplication grailsApplication
    // OutgoingMessageService outgoingMessageService
    // TextService textService

    // AtomicInteger _textSendInvokeCount
    // AtomicInteger _callStartInvokeCount

    // HashSet<String> _receipientNumbers
    // List<Contact> _contacts = []
    // List<ContactTag> _tags = []
    // Phone _phone

    // def setup() {
    //     setupIntegrationData()
    //     IOCUtils.metaClass."static".getLinkGenerator = { ->
    //         [link: { Map m -> "https://www.example.com" }] as LinkGenerator
    //     }
    //     // initializing variables
    //     _textSendInvokeCount = new AtomicInteger(0)
    //     _callStartInvokeCount = new AtomicInteger(0)
    //     // setting up textService
    //     textService = grailsApplication.mainContext.getBean("textService")
    //     textService.metaClass.invokeMethod = { String name, Object args ->
    //         if (name == "send") {
    //             _textSendInvokeCount.getAndIncrement()
    //         }
    //         delegate.metaClass
    //             .getMetaMethod(name, *args)
    //             .invoke(delegate, *args)
    //     }
    //     // setting up callService
    //     callService = grailsApplication.mainContext.getBean("callService")
    //     callService.metaClass.invokeMethod = { String name, Object args ->
    //         if (name == "start") {
    //             _callStartInvokeCount.getAndIncrement()
    //         }
    //         delegate.metaClass
    //             .getMetaMethod(name, *args)
    //             .invoke(delegate, *args)
    //     }
    // }
    // def cleanup() {
    //     cleanupIntegrationData()
    //     // to avoid duplicate errors because we need to use fixed Twilio test numbers
    //     _phone.numberAsString = TestUtils.randPhoneNumberString();
    //     _phone.save(flush:true, failOnError:true)
    // }

    // def mockForOutgoing(String fromNum) {
    //     int numContacts = Constants.MAX_NUM_TEXT_RECIPIENTS
    //     // initialize or refresh instane variables
    //     _phone = p1
    //     _receipientNumbers = new HashSet<>()
    //     _contacts = []
    //     _tags = []
    //     // ensure that phone has appropriate testing number
    //     _phone.number = new PhoneNumber(number:fromNum)
    //     // create contacts
    //     numContacts.times {
    //         Contact c1 = _phone.createContact([:], [TestUtils.randPhoneNumberString()]).payload
    //         _receipientNumbers.addAll(c1.numbers*.e164PhoneNumber)
    //         _contacts << c1
    //     }
    //     _phone.save(flush:true, failOnError:true)
    //     // create tags and add some contacts to each tag
    //     NUM_TAGS.times {
    //         ContactTag ct1 = _phone.createTag(name: TestUtils.randString()).payload
    //         _contacts[0..(TestUtils.randIntegerUpTo(numContacts - 1))].each { Contact c1 ->
    //             ct1.addToMembers(c1)
    //             _receipientNumbers.addAll(c1.numbers*.e164PhoneNumber)
    //         }
    //         _tags << ct1
    //     }
    //     _phone.save(flush:true, failOnError:true)
    // }

    // @DirtiesRuntime
    // void "test sending out high volume of outgoing #type for one TextUp number"() {
    //     given:
    //     RecordItemType enumType = TypeConversionUtils.convertEnum(RecordItemType, type)
    //     boolean isText = (enumType == RecordItemType.TEXT)
    //     _textSendInvokeCount.getAndSet(0)
    //     _callStartInvokeCount.getAndSet(0)
    //     mockForOutgoing(isText ? TestConstants.TEST_SMS_FROM_VALID : TestConstants.TEST_CALL_FROM_VALID)

    //     when: "an outgoing message as text"
    //     OutgoingMessage msg1 = TestUtils.buildOutgoingMessage(p1)
    //     msg1.message = "hello world"
    //     msg1.type = enumType
    //     msg1.contacts.recipients = _contacts
    //     msg1.tags.recipients = _tags
    //     assert msg1.validate()
    //     HashSet<Contactable> recips = msg1.toRecipients()

    //     then: "each contactable is distinct (based on contactId)"
    //     _contacts.size() == recips.size() // num of recipients is the number of contacts
    //     recips.size() == recips*.contactId.unique().size()

    //     when: "sending out to many contacts and tags without staff (for authorship)"
    //     int tBaseline = RecordText.count()
    //     int cBaseline = RecordCall.count()
    //     int iBaseline = RecordItem.count()
    //     Tuple<ResultGroup<RecordItem>, Future<?>> tuple = outgoingMessageService.processMessage(_phone,
    //         msg1, s1)
    //     ResultGroup<RecordItem> resGroup = tuple.first
    //     Future<?> fut1 = tuple.second

    //     then: "successful, no duplicates because all shared contacts resolved into contacts"
    //     resGroup.anySuccesses == true
    //     // double check this number with overall items counter to confirm only appropriate type were added
    //     RecordItem.count() == iBaseline + resGroup.successes.size()

    //     when: "we wait for the future to finish processing + send messages"
    //     fut1.get()

    //     then:
    //     if (isText) {
    //         // no calls made
    //         assert _callStartInvokeCount.intValue() == 0
    //         assert _textSendInvokeCount.intValue() == _contacts.size()
    //         // we are supposed to build a record item for each contactable and tag's record
    //         // _textSendInvokeCount is the number of unique contactables and
    //         // tags is the number of tags so the sum should be all the results in our group
    //         assert _textSendInvokeCount.intValue() + _tags.size() ==
    //             resGroup.successes.size() + resGroup.failures.size()
    //         // each success is a new text stored in either a contactable or tag record
    //         assert RecordText.count() == tBaseline + resGroup.successes.size()
    //     }
    //     else {
    //         // no texts made
    //         assert _textSendInvokeCount.intValue() == 0
    //         assert _callStartInvokeCount.intValue() == _contacts.size()
    //         // we are supposed to build a record item for each contactable and tag's record
    //         // _callStartInvokeCount is the number of unique contactables and
    //         // tags is the number of tags so the sum should be all the results in our group
    //         assert _callStartInvokeCount.intValue() + _tags.size() ==
    //             resGroup.successes.size() + resGroup.failures.size()
    //         // each success is a new text stored in either a contactable or tag record
    //         assert RecordCall.count() == cBaseline + resGroup.successes.size()
    //     }

    //     where:
    //     type | _
    //     RecordItemType.CALL.toString() | _
    //     RecordItemType.TEXT.toString() | _
    // }
}
