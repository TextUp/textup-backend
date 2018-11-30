package org.textup.validator

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.springframework.context.MessageSource
import org.springframework.validation.Errors
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class NumberToContactRecipientsSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test building recipients from string phone number"() {
        given: "empty obj"
        NumberToContactRecipients recips = new NumberToContactRecipients()

        when: "without phone or invalid"
        List<Contact> recipList = recips.buildRecipientsFromIds(["626 123 1234", "626 349 1029"])

        then: "short circuit, returns empty list"
        recipList == []

        when: "with phone and some invalid numbers"
        recips.phone = p1
        recipList = recips.buildRecipientsFromIds(["626 123 1234", "626 349", "291 291"])

        then: "obj has errors"
        recipList.size() == 1 // only build the one valid number
        recips.hasErrors() == false // save the error building for when we call validate

        when: "with phone and all valid numbers"
        recipList = recips.buildRecipientsFromIds(["626 123 1234", "626 349 2910"])

        then: "obj is valid"
        recipList.size() == 2
        recips.hasErrors() == false
    }

    void "test constraints"() {
        when: "empty obj with no recipients"
        NumberToContactRecipients recips = new NumberToContactRecipients()

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

        when: "some invalid numbers"
        recips.ids = ["626 123 1234", "i am not a real phone number"]

        then:
        recips.validate() == false
        recips.errors.getFieldErrorCount("recipients") == 1

        when: "with some ids"
        recips.ids = ["626 123 1234", "626 349 2910"]

        then:
        recips.validate() == true
        recips.ids.size() == 2
        recips.recipients.size() == 2
    }
}
