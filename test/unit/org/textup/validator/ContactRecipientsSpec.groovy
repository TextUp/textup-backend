package org.textup.validator

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.*
import spock.lang.Ignore
import spock.lang.Shared

@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
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
        ContactRecipients recips = new ContactRecipients()

        then: "superclass constraints execute"
        recips.validate() == false
        recips.errors.getFieldErrorCount("phone") == 1

        when: "set phone"
        recips.phone = c1.phone

        then: "valid"
        recips.validate() == true

        when: "array of null ids"
        recips.ids = [null, null]

        then: "null values are ignored"
        recips.validate() == true

        when: "set ids with one foreign contact id + setter populates recipients"
        recips.ids = [c1.id, c2.id]

        then: "invalid foreign id"
        recips.validate() == false
        recips.errors.getFieldErrorCount("recipients") == 1

        when: "setting new ids + setter populates recipients"
        recips.ids = [c1.id, c1_1.id]

        then: "all valid"
        recips.validate() == true
    }
}
