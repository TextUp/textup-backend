package org.textup.validator

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.*
import org.textup.util.CustomSpec
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class ContactRecipientsSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test building recipients from id"() {
        given:
        ContactRecipients recip = new ContactRecipients()

        expect:
        recip.buildRecipientsFromIds([]) == []
        recip.buildRecipientsFromIds([c1.id, c1_1.id]) == [c1, c1_1]
        recip.buildRecipientsFromIds([c1.id, c1_1.id, c2.id]) == [c1, c1_1, c2]
    }

    void "test constraints"() {
        when: "empty obj with no recipients"
        ContactRecipients recip = new ContactRecipients()

        then: "superclass constraints execute"
        recip.validate() == false
        recip.errors.getFieldErrorCount("phone") == 1

        when: "set phone"
        recip.phone = c1.phone

        then: "valid"
        recip.validate() == true

        when: "set ids with one foreign contact id + setter populates recipients"
        recip.ids = [c1.id, c2.id]

        then: "invalid foreign id"
        recip.validate() == false
        recip.errors.getFieldErrorCount("recipients") == 1

        when: "setting new ids + setter populates recipients"
        recip.ids = [c1.id, c1_1.id]

        then: "all valid"
        recip.validate() == true
    }
}
