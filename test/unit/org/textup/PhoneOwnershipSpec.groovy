package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.textup.types.PhoneOwnershipType
import org.textup.types.StaffStatus
import org.textup.util.CustomSpec
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
	Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class PhoneOwnershipSpec extends CustomSpec {

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
    	when: "we have an empty phone ownership"
    	PhoneOwnership own = new PhoneOwnership()

    	then: "invalid"
    	own.validate() == false
    	own.errors.errorCount == 3

    	when: "we fill out fields"
    	own = new PhoneOwnership(phone:p1, type:PhoneOwnershipType.INDIVIDUAL,
    		ownerId:s1.id)

    	then: "valid"
    	own.validate() == true
    }

    void "test getting owners"() {
    	given: "team has all active staff"
    	t1.members.each { it.status = StaffStatus.STAFF }
    	t1.save(flush:true, failOnError:true)

    	when: "we have an individual phone ownership"
    	PhoneOwnership own = new PhoneOwnership(phone:p1, ownerId:s1.id,
    		type:PhoneOwnershipType.INDIVIDUAL)

    	then:
    	own.validate() == true
    	own.name == s1.name
    	own.all.size() == 1
    	own.all[0] == s1

    	when: "we have a group phone ownership"
    	own = new PhoneOwnership(phone:p1, ownerId:t1.id,
    		type:PhoneOwnershipType.GROUP)

    	then:
    	own.validate() == true
    	own.name == t1.name
    	own.all.size() == t1.members.size()
    	own.all == t1.members
    }
}
