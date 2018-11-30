package org.textup.validator

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.type.PhoneOwnershipType

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class BasicNotificationSpec extends CustomSpec {

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
		when: "an empty notification"
		BasicNotification bNotif1 = new BasicNotification()

		then: "invalid"
		bNotif1.validate() == false
		bNotif1.errors.errorCount == 2

		when: "fill out staff"
		bNotif1.staff = s1

		then: "optional so no change in errors"
		bNotif1.validate() == false
		bNotif1.errors.errorCount == 2

		when: "fill out info"
		bNotif1.owner = p1.owner
		bNotif1.record = c1.record

		then: "valid"
		bNotif1.validate() == true
		if (p1.owner.type == PhoneOwnershipType.INDIVIDUAL) {
			bNotif1.ownerId == p1.owner.all[0].username
			bNotif1.ownerType == "staff"
		}
		else {
			bNotif1.ownerId ==  Team.get(p1.owner.ownerId).name
			bNotif1.ownerType ==	"team"
		}
	}
}
