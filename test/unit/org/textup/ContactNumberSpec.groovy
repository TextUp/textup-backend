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
@Unroll
class ContactNumberSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test constraints"() {
        when: "we have an empty contact number"
        ContactNumber cn = new ContactNumber()

        then: "invalid"
        cn.validate() == false
        cn.errors.errorCount == 3

        when: "we fill out fields"
        String number = "1233940349"
        cn = new ContactNumber(preference:8, owner:c1, number:number)

        then: "valid"
        cn.validate() == true

        when: "we officially add to relationship"
        c1.addToNumbers(cn)
        c1.save(flush:true, failOnError:true)

        then: "getting contacts for phone and numbers"
        ContactNumber.getContactsForPhoneAndNumbers(p1,
            ["2390254789", number])[number] == [c1]

        when: "mark contact as deleted"
        c1.isDeleted = true
        c1.save(flush:true, failOnError:true)

        then: "no results for the number specified"
        ContactNumber.getContactsForPhoneAndNumbers(p1,
            ["2390254789", number])[number].isEmpty() == true
    }
}
