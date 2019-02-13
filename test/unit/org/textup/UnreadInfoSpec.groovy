package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class UnreadInfoSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creating"() {
        given:
        Record rec1 = TestUtils.buildRecord()
        RecordText rText1 = new RecordText(record: rec1, outgoing: false)
        RecordText rText2 = new RecordText(record: rec1, outgoing: true)
        RecordCall rCall1 = new RecordCall(record: rec1, outgoing: false)
        RecordCall rCall2 = new RecordCall(record: rec1, outgoing: false, voicemailInSeconds: 22, hasAwayMessage: true)
        RecordCall rCall3 = new RecordCall(record: rec1, outgoing: true)
        RecordNote rNote1 = new RecordNote(record: rec1)
        [rText1, rText2, rCall1, rCall2, rCall3, rNote1]*.save(flush:true, failOnError:true)

        when:
        UnreadInfo uInfo = UnreadInfo.create(null, null)

        then:
        uInfo.numTexts == 0
        uInfo.numCalls == 0
        uInfo.numVoicemails == 0

        when:
        uInfo = UnreadInfo.create(rec1.id, DateTime.now().minusDays(2))

        then: "unread info only includes counts for incoming items"
        uInfo.numTexts == 1 // excludes outgoing text
        uInfo.numCalls == 1 // excludes outgoing call and call with voicemail
        uInfo.numVoicemails == 1
    }
}
