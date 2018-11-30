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

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class ContactTagRecipientsSpec extends CustomSpec {

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
        ContactTagRecipients recips = new ContactTagRecipients()

        expect:
        recips.buildRecipientsFromIds([]) == []
        recips.buildRecipientsFromIds([tag1.id, tag1_1.id, tag2.id]) == [tag1, tag1_1, tag2]
    }

    void "test constraints"() {
        when: "empty obj with no recipients"
        ContactTagRecipients recips = new ContactTagRecipients()

        then: "superclass constraints execute"
        recips.validate() == false
        recips.errors.getFieldErrorCount("phone") == 1

        when: "with phone"
        recips.phone = p1

        then:
        recips.validate() == true

        when: "array of null ids"
        recips.ids = [null, null]

        then: "null values are ignored"
        recips.validate() == true

        when: "set ids with one foreign id + setter populates recipients"
        recips.ids = [tag1.id, tag1_1.id, tag2.id]

        then: "invalid foreign id"
        recips.validate() == false
        recips.errors.getFieldErrorCount("recipients") == 1

        when: "setting new ids + setter populates recipients"
        recips.ids = [tag1.id, tag1_1.id]

        then: "all valid"
        recips.validate() == true
    }
}
