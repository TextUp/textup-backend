package org.textup.util

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.*
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class RecordUtilsSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    def cleanup() {
        cleanupData()
    }

    void "test building record item request"() {
        given:
        TypeConvertingMap body = new TypeConvertingMap()
        DateTime start = DateTime.now().minusDays(2)
        DateTime end = DateTime.now()

        when: "missing inputs"
        Result<RecordItemRequest> res = RecordUtils.buildRecordItemRequest(null, body, false)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "no params"
        res = RecordUtils.buildRecordItemRequest(p1, body, false)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof RecordItemRequest
        res.payload.phone == p1
        res.payload.groupByEntity == false

        when: "has params"
        assert sc2.sharedWith.id == p1.id

        body = new TypeConvertingMap(
            since: start.toString(),
            before: end.toString(),
            "types[]": ["text"],
            "contactIds[]": [c1.id],
            "sharedContactIds[]":[sc2.contactId],
            "tagIds[]": [tag1.id]
        )
        res = RecordUtils.buildRecordItemRequest(p1, body, false)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof RecordItemRequest
        res.payload.start == start
        res.payload.end == end
        res.payload.types == [RecordText]
        res.payload.contacts.recipients == [c1]
        res.payload.sharedContacts.recipients == [sc2]
        res.payload.tags.recipients == [tag1]
    }

    // Identification
    // --------------

    void "test parsing types"() {
        given: "a record with one of each type"
        Record rec1 = new Record()
        rec1.save(flush:true, failOnError:true)

        RecordText rText1 = rec1.storeOutgoingText("hi").payload
        RecordCall rCall1 = rec1.storeOutgoingCall().payload
        RecordNote rNote1 = new RecordNote(record: rec1)
        [rText1, rCall1, rNote1]*.save(flush:true, failOnError:true)

        when:
        List<Class<? extends RecordItem>> clazzes = RecordUtils.parseTypes(typesFilter)

        then:
        clazzes.size() == numResults

        where:
        typesFilter              | numResults
        ["call"]                 | 1
        ["text"]                 | 1
        ["note"]                 | 1
        ["call", "text"]         | 2
        ["text", "note"]         | 2
        ["call", "text", "note"] | 3
        ["BAD!", "text", "note"] | 2
    }

    void "test determine class"() {
        when: "unknown entity"
        Result res = RecordUtils.determineClass([:])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordUtils.determineClass.unknownType"

        when: "text"
        res = RecordUtils.determineClass([sendToContacts:[1]])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordText

        when: "note"
        res = RecordUtils.determineClass([forContact:1])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordNote

        when: "call"
        res = RecordUtils.determineClass([callContact:c1.id])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordCall
    }

    // Texts
    // -----

    @DirtiesRuntime
    void "test check outgoing message recipients"() {
        given:
        OutgoingMessage msg1 = TestUtils.buildOutgoingMessage(p1)

        when: "no recipients"
        Result<OutgoingMessage> res = RecordUtils.checkOutgoingMessageRecipients(msg1)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "recordUtils.atLeastOneRecipient"

        when: "valid number of recipients"
        msg1.contacts.recipients = [c1]
        res = RecordUtils.checkOutgoingMessageRecipients(msg1)

        then:
        res.status == ResultStatus.OK

        when: "too many recipients"
        TestUtils.mock(msg1, "toRecipients") {
            Collection<Contactable> recipients = []
            (Constants.MAX_NUM_TEXT_RECIPIENTS + 1).times { recipients << c1 }
            recipients
        }
        res = RecordUtils.checkOutgoingMessageRecipients(msg1)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordUtils.checkOutgoingMessageRecipients.tooMany"
    }

    void "test building outgoing message target"() {
        given:
        int cBaseline = Contact.count()
        int cnBaseline = ContactNumber.count()

        when: "contacts not belonging to me"
        TypeConvertingMap body = new TypeConvertingMap(contents: "hi", sendToContacts: [c2.id])
        assert c2.phone != p1
        Result<OutgoingMessage> res = RecordUtils.buildOutgoingMessageTarget(p1, body, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "expired shared contacts"
        assert sc2.sharedWith == p1
        body = new TypeConvertingMap(contents: "hi", sendToSharedContacts: [sc2.contactId])
        sc2.stopSharing()
        sc2.save(flush: true, failOnError: true)
        res = RecordUtils.buildOutgoingMessageTarget(p1, body, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "not my tags"
        body = new TypeConvertingMap(contents: "hi", sendToTags: [tag2.id])
        assert tag2.phone != p1
        res = RecordUtils.buildOutgoingMessageTarget(p1, body, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "invalid phone numbers"
        body = new TypeConvertingMap(contents: "hi", sendToPhoneNumbers: ["i am not a valid phone number"])
        res = RecordUtils.buildOutgoingMessageTarget(p1, body, null)

        then: "invalid phone numbers are ignored"
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "recordUtils.atLeastOneRecipient"
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "valid, no recipients"
        body = new TypeConvertingMap(contents: "hi")
        res = RecordUtils.buildOutgoingMessageTarget(p1, body, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "recordUtils.atLeastOneRecipient"
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline

        when: "valid, with recipients"
        sc2.startSharing(ContactStatus.ACTIVE, SharePermission.DELEGATE)
        sc2.save(flush: true, failOnError: true)
        body = new TypeConvertingMap(contents: "hi",
            sendToContacts: [c1.id],
            sendToSharedContacts: [sc2.contactId],
            sendToTags: [tag1.id],
            sendToPhoneNumbers: [TestUtils.randPhoneNumber()])
        res = RecordUtils.buildOutgoingMessageTarget(p1, body, null)
        Contact.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        Contact.count() == cBaseline + 1
        ContactNumber.count() == cnBaseline + 1
    }

    // Calls
    // -----

    void "test building outgoing call target"() {
        given:
        Phone thisPhone = t1.phone
        assert tC1.phone == thisPhone
        sc1.sharedWith = thisPhone
        sc1.save(flush: true, failOnError: true)

        when: "no recipients"
        TypeConvertingMap body = new TypeConvertingMap()
        Result<Contactable> res = RecordUtils.buildOutgoingCallTarget(thisPhone, body)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages.contains("recordUtils.atLeastOneRecipient")

        when: "both contact and shared contact provided"
        body = new TypeConvertingMap(callContact: tC1.id, callSharedContact: sc1.contactId)
        res = RecordUtils.buildOutgoingCallTarget(thisPhone, body)

        then: "use contact, arbitrarily choose one first, still valid, multiple recipients check happens in controller"
        res.status == ResultStatus.OK
        res.payload instanceof Contactable
        res.payload.contactId == tC1.id

        when: "only valid contact id for a shared contact"
        body = new TypeConvertingMap(callSharedContact: sc1.contactId)
        res = RecordUtils.buildOutgoingCallTarget(thisPhone, body)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof Contactable
        res.payload.contactId == sc1.contactId
    }

    // Notes
    // -----

    void "test building note target"() {
        when: "no recipients"
        TypeConvertingMap body = new TypeConvertingMap([:])
        Result<Record> res = RecordUtils.buildNoteTarget(t1.phone, body)

        then: "error"
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages.contains("recordUtils.atLeastOneRecipient")

        when: "multiple recipients"
        body = new TypeConvertingMap(forContact: tC1.id, forSharedContact: sc1.contactId)
        res = RecordUtils.buildNoteTarget(sc1.sharedWith, body)
        RecordNote.withSession { it.flush() }

        then: "arbitrarily choose one first, still valid, multiple recipients check happens in controller"
        res.status == ResultStatus.OK
        res.payload instanceof Record

        when: "creating for a contact"
        body = new TypeConvertingMap(forContact: tC1.id)
        res = RecordUtils.buildNoteTarget(t1.phone, body)
        RecordNote.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload == tC1.record

        when: "creating for a shared contact"
        body = new TypeConvertingMap(forSharedContact: sc1.contactId)
        res = RecordUtils.buildNoteTarget(sc1.sharedWith, body)
        RecordNote.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload == sc1.contact.record

        when: "creating for a tag"
        body = new TypeConvertingMap(forTag: tag1.id)
        res = RecordUtils.buildNoteTarget(tag1.phone, body)
        RecordNote.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload == tag1.record
    }
}
