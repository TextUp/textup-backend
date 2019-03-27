package org.textup.util

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class RecordUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test determine class"() {
        when: "unknown entity"
        Result res = RecordUtils.tryDetermineClass(TypeMap.create())

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "recordUtils.noClassForUnknownType"

        when: "text"
        res = RecordUtils.tryDetermineClass(TypeMap.create(type: "text"))

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordText

        when: "note"
        res = RecordUtils.tryDetermineClass(TypeMap.create(type: "note"))

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordNote

        when: "call"
        res = RecordUtils.tryDetermineClass(TypeMap.create(type: "call"))

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == RecordCall
    }

    void "test adjusting position in record based on `whenCreated` timestamp"() {
        given:
        DateTime dt = DateTime.now().minusHours(1)

        Record rec1 = TestUtils.buildRecord()
        Record rec2 = TestUtils.buildRecord()
        RecordItem rItem1 = TestUtils.buildRecordItem(rec2)
        RecordItem rItem2 = TestUtils.buildRecordItem(rec2)
        rItem2.whenCreated = rItem1.whenCreated.plusYears(1)

        RecordItem.withSession { it.flush() }

        when: "no before item found"
        DateTime retDt = RecordUtils.adjustPosition(rec1.id, dt)

        then: "return the current time as the timestamp because no need to manipulate `whenCreated`"
        retDt.isAfter(dt)

        when: "yes before item found"
        retDt = RecordUtils.adjustPosition(rec2.id, dt)

        then: "placed between the after time and the oldest record item (`rItem1`)"
        retDt.isAfter(dt)
        retDt.isBefore(rItem1.whenCreated)
        retDt.isBefore(rItem2.whenCreated)
        retDt.isAfter(dt.plus(ValidationUtils.MIN_NOTE_SPACING_MILLIS))
        // note that passed-in time is one HOUR ago so we definitely hit the max spacing
        retDt.isEqual(dt.plus(ValidationUtils.MAX_NOTE_SPACING_MILLIS))
    }

    void "test building record item request"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(null, p1)
        DateTime start = DateTime.now().minusDays(2)
        DateTime end = DateTime.now()

        when: "missing inputs"
        Result<RecordItemRequest> res = RecordUtils.buildRecordItemRequest(null, TypeMap.create())

        then:
        res.status == ResultStatus.NOT_FOUND

        when: "no params"
        res = RecordUtils.buildRecordItemRequest(p1.id, TypeMap.create())

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof RecordItemRequest
        res.payload.mutablePhone == p1
        res.payload.groupByEntity == false

        when: "has params"
        res = RecordUtils.buildRecordItemRequest(p1.id, TypeMap.create(start: start.toString(),
            end: end.toString(),
            "types[]": ["text"],
            "owners[]": [ipr1, gpr1, spr1]*.id))

        then:
        res.status == ResultStatus.CREATED
        res.payload instanceof RecordItemRequest
        res.payload.mutablePhone == p1
        res.payload.start == start
        res.payload.end == end
        res.payload.types == [RecordText]
        ipr1.toWrapper() in res.payload.wrappers
        gpr1.toWrapper() in res.payload.wrappers
        spr1.toWrapper() in res.payload.wrappers
    }

    void "test building single section"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        RecordItem rItem1 = TestUtils.buildRecordItem()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

        when:
        RecordItemRequestSection section1 = RecordUtils.buildSingleSection(null, null, null)

        then:
        section1 == null

        when:
        section1 = RecordUtils.buildSingleSection(p1, [rItem1], [ipr1.toWrapper()])

        then:
        section1 != null
        section1.contactNames.size() == 1
        section1.phoneNumber == p1.number.prettyPhoneNumber
        section1.recordItems*.id == [rItem1]*.id
    }

    void "test building sections by entity"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(gpr1.record)

        when:
        List sections = RecordUtils.buildSectionsByEntity(null, null)

        then:
        sections == []

        when:
        sections = RecordUtils.buildSectionsByEntity([rItem2, rItem1], [ipr1, gpr1]*.toWrapper())

        then:
        sections.size() == 2
        sections.find {
            it.phoneNumber == ipr1.phone.number.prettyPhoneNumber && it.recordItems*.id == [rItem1]*.id
        }
        sections.find {
            it.phoneNumber == gpr1.phone.number.prettyPhoneNumber && it.recordItems*.id == [rItem2]*.id
        }
    }
}
