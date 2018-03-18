package org.textup

import grails.test.runtime.FreshRuntime
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.type.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import spock.lang.Unroll

@Unroll
class PhoneServiceIntegrationSpec extends CustomSpec {

    final int NUM_TAGS = 20

    def phoneService
    def grailsApplication

    TextService textService
    def _originalLinkForText
    AtomicInteger _textSendInvokeCount

    CallService callService
    def _originalLinkForCall
    AtomicInteger _callStartInvokeCount

    HashSet<String> _receipientNumbers
    List<Contact> _contacts = []
    List<ContactTag> _tags = []
    Phone _phone

    def setup() {
        setupIntegrationData()
        // initializing variables
        _textSendInvokeCount = new AtomicInteger(0)
        _callStartInvokeCount = new AtomicInteger(0)
        // setting up textService
        textService = grailsApplication.mainContext.getBean("textService")
        _originalLinkForText = textService.grailsLinkGenerator
        textService.grailsLinkGenerator = [
            link: { Map params -> "https://www.example.com" }
        ] as LinkGenerator
        textService.metaClass.invokeMethod = { String name, Object args ->
            if (name == "send") {
                _textSendInvokeCount.getAndIncrement()
            }
            delegate.metaClass
                .getMetaMethod(name, *args)
                .invoke(delegate, *args)
        }
        // setting up callService
        callService = grailsApplication.mainContext.getBean("callService")
        _originalLinkForCall = callService.grailsLinkGenerator
        callService.grailsLinkGenerator = [
            link: { Map params -> "https://www.example.com" }
        ] as LinkGenerator
        callService.metaClass.invokeMethod = { String name, Object args ->
            if (name == "start") {
                _callStartInvokeCount.getAndIncrement()
            }
            delegate.metaClass
                .getMetaMethod(name, *args)
                .invoke(delegate, *args)
        }
    }
    def cleanup() {
        cleanupIntegrationData()
        if (_originalLinkForText) {
            textService.grailsLinkGenerator = _originalLinkForText
        }
        if (_originalLinkForCall) {
            callService.grailsLinkGenerator = _originalLinkForCall
        }
    }

    def mockForOutgoing(String fromNum) {
        int numContacts = grailsApplication.config.textup.maxNumText
        // initialize or refresh instane variables
        _phone = p1
        _receipientNumbers = new HashSet<>()
        _contacts = []
        _tags = []
        // ensure that phone has appropriate testing number
        _phone.number = new PhoneNumber(number:fromNum)
        // create contacts
        numContacts.times {
            Contact c1 = _phone.createContact([:], [randPhoneNumber()]).payload
            _receipientNumbers.addAll(c1.numbers*.e164PhoneNumber)
            _contacts << c1
        }
        _phone.save(flush:true, failOnError:true)
        // create tags and add some contacts to each tag
        NUM_TAGS.times {
            ContactTag ct1 = _phone.createTag(name:UUID.randomUUID().toString()).payload
            _contacts[0..(randIntegerUpTo(numContacts - 1))].each { Contact c1 ->
                ct1.addToMembers(c1)
                _receipientNumbers.addAll(c1.numbers*.e164PhoneNumber)
            }
            _tags << ct1
        }
        _phone.save(flush:true, failOnError:true)
    }

    @FreshRuntime
    void "test sending out high volume of outgoing #type for one TextUp number"() {
        given:
        RecordItemType enumType = Helpers.convertEnum(RecordItemType, type)
        boolean isText = (enumType == RecordItemType.TEXT)
        _textSendInvokeCount.getAndSet(0)
        _callStartInvokeCount.getAndSet(0)
        mockForOutgoing(isText ? Constants.TEST_SMS_FROM_VALID : Constants.TEST_CALL_FROM_VALID)

        when: "an outgoing message as text"
        OutgoingMessage msg1 = new OutgoingMessage(message:"hello world",
            type:enumType, contacts:_contacts, tags:_tags)
        assert msg1.validateSetPhone(_phone)
        HashSet<Contactable> recips = msg1.toRecipients()

        then: "each contactable is distinct (based on contactId)"
        _contacts.size() == recips.size() // num of recipients is the number of contacts
        recips.size() == recips*.contactId.unique().size()

        when: "sending out to many contacts and tags without staff (for authorship)"
        int numRecordTexts = RecordText.count()
        int numRecordCalls = RecordCall.count()
        int numRecordItems = RecordItem.count()
        ResultGroup<RecordItem> resGroup = phoneService.sendMessage(_phone, msg1)

        then: "successful, no duplicates because all shared contacts resolved into contacts"
        resGroup.anySuccesses == true
        // double check this number with overall items counter to confirm only appropriate type were added
        RecordItem.count() == numRecordItems + resGroup.successes.size()
        if (isText) {
            // no calls made
            assert _callStartInvokeCount.intValue() == 0
            // some of the results in the result group are from storing the message in tag records
            assert _textSendInvokeCount.intValue() + _tags.size() ==
                resGroup.successes.size() + resGroup.failures.size()
            // each success is a new text stored in either a contactable or tag record
            assert RecordText.count() == numRecordTexts + resGroup.successes.size()
        }
        else {
            // no texts made
            assert _textSendInvokeCount.intValue() == 0
            // some of the results in the result group are from storing the message in tag records
            assert _callStartInvokeCount.intValue() + _tags.size() ==
                resGroup.successes.size() + resGroup.failures.size()
            // each success is a new text stored in either a contactable or tag record
            assert RecordCall.count() == numRecordCalls + resGroup.successes.size()
        }

        where:
        type | _
        RecordItemType.CALL.toString() | _
        RecordItemType.TEXT.toString() | _
    }
}
