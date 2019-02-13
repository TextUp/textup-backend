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
class AnnouncementInfoSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement()
        AnnouncementReceipt aRpt1 = TestUtils.buildAnnouncementReceipt(fa1)
        aRpt1.type = RecordItemType.TEXT
        AnnouncementReceipt aRpt2 = TestUtils.buildAnnouncementReceipt(fa1)
        aRpt2.type = RecordItemType.CALL

        when:
        AnnouncementInfo aInfo = AnnouncementInfo.create(null)

        then:
        aInfo.recipients.isEmpty()
        aInfo.callRecipients.isEmpty()
        aInfo.textRecipients.isEmpty()

        when:
        aInfo = AnnouncementInfo.create(fa1)

        then:
        aInfo.recipients.size() == 2
        aRpt1.session.number in aInfo.recipients
        aRpt2.session.number in aInfo.recipients

        aInfo.textRecipients.size() == 1
        aRpt1.session.number in aInfo.textRecipients

        aInfo.callRecipients.size() == 1
        aRpt2.session.number in aInfo.callRecipients
    }
}
