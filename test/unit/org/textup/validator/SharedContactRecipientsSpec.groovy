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
class SharedContactRecipientsSpec extends CustomSpec {

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
        when: "without phone"
        SharedContactRecipients recip = new SharedContactRecipients()

        then: "empty result"
        recip.buildRecipientsFromIds([1, 2, 3]) == Collections.emptyList()

        when: "with phone"
        recip.phone = p1

        then: "appropriate shared contacts from contact ids"
        recip.buildRecipientsFromIds([sc2.contactId]) == [sc2]
    }

    void "test constraints"() {
        when: "empty obj with no recipients"
        SharedContactRecipients recips = new SharedContactRecipients()

        then: "superclass constraints execute"
        recips.validate() == false
        recips.errors.getFieldErrorCount("phone") == 1

        when: "with phone"
        recips.phone = p1

        then: "valid"
        recips.validate() == true

        when: "array of null ids"
        recips.ids = [null, null]

        then: "null values are ignored"
        recips.validate() == true

        when: "set ids with one not shared contact id + setter populates recipients"
        recips.ids = [sc1.contactId, sc2.contactId]

        then: "invalid not shared id"
        recips.validate() == false
        recips.errors.getFieldErrorCount("recipients") == 1

        when: "setting new ids + setter populates recipients"
        recips.ids = [sc2.contactId]

        then: "all valid"
        recips.validate() == true
    }
}
