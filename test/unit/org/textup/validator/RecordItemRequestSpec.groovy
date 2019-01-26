package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

// TODO

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class RecordItemRequestSpec extends CustomSpec {

    // static doWithSpring = {
    //     resultFactory(ResultFactory)
    // }

    // def setup() {
    //     setupData()
    // }

    // def cleanup() {
    //     cleanupData()
    // }

    // void "test properties + validation"() {
    //     when: "empty"
    //     RecordItemRequest iReq = new RecordItemRequest()

    //     then:
    //     iReq.validate() == false

    //     when: "all required filled out but contained objects are invalid"
    //     iReq.with {
    //         phone = p1
    //         contacts = new ContactRecipients()
    //         sharedContacts = new SharedContactRecipients()
    //         tags = new ContactTagRecipients()
    //     }

    //     then:
    //     iReq.validate() == false
    //     iReq.errors.getFieldErrorCount("contacts.phone") == 1
    //     iReq.errors.getFieldErrorCount("sharedContacts.phone") == 1
    //     iReq.errors.getFieldErrorCount("tags.phone") == 1

    //     when:
    //     iReq.contacts.phone = p1
    //     iReq.sharedContacts.phone = p1
    //     iReq.tags.phone = p1

    //     then:
    //     iReq.validate() == true
    // }

    // void "test if has any recipients"() {
    //     when: "no recipients"
    //     RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)

    //     then:
    //     iReq.hasAnyRecipients() == false

    //     when: "has recipients"
    //     iReq.contacts.recipients << c1

    //     then:
    //     iReq.hasAnyRecipients() == true
    // }

    // void "test counting + getting record items"() {
    //     when: "get all records from phone"
    //     RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
    //     c1.phone = p1
    //     RecordItem rItem1 = c1.record.storeOutgoingText(TestUtils.randString()).payload
    //     tag1.phone = p1
    //     RecordItem rItem2 = tag1.record.storeOutgoingCall().payload
    //     RecordItem rItem3 = tag1.record.storeOutgoingCall().payload

    //     rItem1.whenCreated = DateTime.now()
    //     rItem2.whenCreated = DateTime.now().minusDays(2)
    //     rItem3.whenCreated = DateTime.now().minusDays(8)

    //     Record.withSession { it.flush() }

    //     then: "default sort is recent first"
    //     iReq.countRecordItems() > 0
    //     iReq.recordItems.findIndexOf { it.id == rItem1.id } <
    //         iReq.recordItems.findIndexOf { it.id == rItem2.id }
    //     iReq.getRecordItems(null, false) .findIndexOf { it.id == rItem1.id } >
    //         iReq.getRecordItems(null, false).findIndexOf { it.id == rItem2.id }

    //     when: "get records for specified record owners"
    //     iReq.tags.recipients << tag1

    //     then: "only record items for specified tag"
    //     iReq.countRecordItems() > 0
    //     -1 == iReq.recordItems.findIndexOf { it.id == rItem1.id }
    //     iReq.recordItems.findIndexOf { it.id == rItem2.id } <
    //         iReq.recordItems.findIndexOf { it.id == rItem3.id }
    //     iReq.getRecordItems(null, false) .findIndexOf { it.id == rItem2.id } >
    //         iReq.getRecordItems(null, false).findIndexOf { it.id == rItem3.id }
    // }

    // @DirtiesRuntime
    // void "test getting record items normalizes pagination parameters"() {
    //     given:
    //     MockedMethod normalizePagination = TestUtils.mock(Utils, "normalizePagination") { [] }
    //     Integer offset = 88
    //     Integer max = 888

    //     RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)

    //     when:
    //     iReq.getRecordItems(offset: offset, max: max)

    //     then:
    //     normalizePagination.callCount == 1
    //     normalizePagination.callArguments[0] == [offset, max]
    // }

    // void "test getting record ids"() {
    //     given:
    //     RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
    //     iReq.contacts.recipients << c1
    //     iReq.sharedContacts.recipients << sc1
    //     iReq.tags.recipients << tag1

    //     when:
    //     Collection<Long> recIds = iReq.recordIds

    //     then:
    //     recIds.size() == 3
    //     c1.record.id in recIds
    //     sc1.contact.record.id in recIds
    //     tag1.record.id in recIds
    // }

    // void "test getting sections when getting all records for a phone"() {
    //     given:
    //     RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
    //     c1.phone = p1
    //     RecordItem rItem1 = c1.record.storeOutgoingText(TestUtils.randString()).payload
    //     tag1.phone = p1
    //     RecordItem rItem2 = tag1.record.storeOutgoingCall().payload

    //     Record.withSession { it.flush() }

    //     when: "sections as single stream"
    //     iReq.groupByEntity = false
    //     List<RecordItemRequestSection> sections = iReq.sections

    //     then:
    //     sections.size() == 1
    //     sections[0].phoneName == p1.owner.buildName()
    //     sections[0].phoneNumber == p1.number.prettyPhoneNumber
    //     sections[0].contactNames == []
    //     sections[0].tagNames == []
    //     sections[0].sharedContactNames == []
    //     sections[0].recordItems.size() > 0

    //     when: "sections as grouped by entity"
    //     iReq.groupByEntity = true
    //     sections = iReq.sections

    //     then: "when getting from phone, grouped by entity is the same as single stream"
    //     sections.size() == 1
    //     sections[0].phoneName == p1.owner.buildName()
    //     sections[0].phoneNumber == p1.number.prettyPhoneNumber
    //     sections[0].contactNames == []
    //     sections[0].tagNames == []
    //     sections[0].sharedContactNames == []
    //     sections[0].recordItems.size() > 0
    // }

    // void "test getting sections when getting records for specified record owners"() {
    //     given:
    //     RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
    //     iReq.contacts.recipients << c1
    //     iReq.sharedContacts.recipients << sc2
    //     iReq.tags.recipients << tag1

    //     assert sc2.sharedBy.id != p1.id

    //     RecordItem rItem1 = c1.record.storeOutgoingText(TestUtils.randString()).payload
    //     RecordItem rItem2 = sc2.contact.record.storeOutgoingText(TestUtils.randString()).payload
    //     RecordItem rItem3 = tag1.record.storeOutgoingText(TestUtils.randString()).payload

    //     Record.withSession { it.flush() }

    //     when: "sections as single stream"
    //     iReq.groupByEntity = false
    //     List<RecordItemRequestSection> sections = iReq.sections

    //     then:
    //     sections.size() == 1
    //     sections[0].phoneName == p1.owner.buildName()
    //     sections[0].phoneNumber == p1.number.prettyPhoneNumber
    //     sections[0].contactNames == [c1.nameOrNumber]
    //     sections[0].sharedContactNames == [sc2.name]
    //     sections[0].tagNames == [tag1.name]
    //     sections[0].recordItems.size() > 0
    //     rItem1.id in sections[0].recordItems*.id
    //     rItem2.id in sections[0].recordItems*.id
    //     rItem3.id in sections[0].recordItems*.id

    //     when: "sections as grouped by entity"
    //     iReq.groupByEntity = true
    //     sections = iReq.sections

    //     then: "when a shared contact is alone in its section, the phone for that section is the sharedBy phone"
    //     sections.size() == 3
    //     sections.any { it.phoneName == sc2.sharedBy.owner.buildName() }
    //     sections.any { it.phoneNumber == sc2.sharedBy.number.prettyPhoneNumber }
    //     sections.every {
    //         it.phoneName in [sc2.sharedBy.owner.buildName(), p1.owner.buildName()]
    //     }
    //     sections.every {
    //         it.phoneNumber in [sc2.sharedBy.number.prettyPhoneNumber, p1.number.prettyPhoneNumber]
    //     }
    //     sections.any { it.contactNames == [c1.nameOrNumber] && rItem1.id in it.recordItems*.id }
    //     sections.any { it.sharedContactNames == [sc2.name] && rItem2.id in it.recordItems*.id }
    //     sections.any { it.tagNames == [tag1.name] && rItem3.id in it.recordItems*.id }
    // }

    // void "test passing on datetime and type criteria, if specified"() {
    //     given:
    //     RecordItemRequest iReq = TestUtils.buildRecordItemRequest(p1)
    //     iReq.contacts.recipients << c1
    //     iReq.sharedContacts.recipients << sc1
    //     iReq.tags.recipients << tag1

    //     RecordItem rText1 = c1.record.storeOutgoingText(TestUtils.randString()).payload
    //     RecordItem rText2 = tag1.record.storeOutgoingText(TestUtils.randString()).payload
    //     RecordItem rCall1 = sc1.contact.record.storeOutgoingCall().payload

    //     rText1.whenCreated = DateTime.now().minusDays(8)
    //     rText2.whenCreated = DateTime.now().minusDays(2)
    //     rCall1.whenCreated = DateTime.now()

    //     Record.withSession { it.flush() }

    //     when: "no criteria specified"
    //     List<RecordItem> recList = iReq.recordItems

    //     then: "show all"
    //     rText1.id in recList*.id
    //     rText2.id in recList*.id
    //     rCall1.id in recList*.id

    //     when: "with date criteria"
    //     iReq.start = rText1.whenCreated.plusDays(1)
    //     recList = iReq.recordItems

    //     then:
    //     (rText1.id in recList*.id) == false
    //     rText2.id in recList*.id
    //     rCall1.id in recList*.id

    //     when: "with date + type criteria"
    //     iReq.types = [RecordText]
    //     recList = iReq.recordItems

    //     then:
    //     (rText1.id in recList*.id) == false
    //     rText2.id in recList*.id
    //     (rCall1.id in recList*.id) == false
    // }
}
