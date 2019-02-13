package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class RecordNotesSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding modifiable given id"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        RecordNote rNote1 = new RecordNote(record: rec1)
        RecordNote rNote2 = new RecordNote(record: rec1, isReadOnly: true)
        [rNote1, rNote2]*.save(flush: true, failOnError: true)

        when:
        Result res = RecordNotes.mustFindModifiableForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = RecordNotes.mustFindModifiableForId(rNote2.id)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = RecordNotes.mustFindModifiableForId(rNote1.id)

        then:
        res.status == ResultStatus.OK
        res.payload == rNote1
    }
}
