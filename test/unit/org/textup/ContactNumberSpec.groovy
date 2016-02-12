package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Ignore
import spock.lang.Shared
import org.textup.util.CustomSpec
import spock.lang.Unroll

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
  RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
  Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
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
    }
}
