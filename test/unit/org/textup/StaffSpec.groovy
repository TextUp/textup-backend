package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeZone
import org.joda.time.LocalTime
import org.springframework.context.MessageSource
import org.textup.types.AuthorType
import org.textup.types.ScheduleStatus
import org.textup.util.CustomSpec
import org.textup.validator.Author
import org.textup.validator.LocalInterval
import org.textup.validator.ScheduleChange
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Unroll

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole])
@TestMixin(HibernateTestMixin)
@Unroll
class StaffSpec extends CustomSpec {

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
    	when: "we have an empty staff"
    	Staff staff1 = new Staff()

    	then:
    	staff1.validate() == false

    	when: "we have fill in info except for invalid email"
    	String un = "8stAFf"
		staff1.username = un
		staff1.password = "password"
		staff1.name = "Staff"
		staff1.email = "invalid email"
		staff1.org = org
		staff1.personalPhoneAsString = "1112223333"

    	then:
    	staff1.getUsername() == un.toLowerCase()
    	staff1.validate() == false
    	staff1.errors.errorCount == 1
    	staff1.errors.getFieldErrorCount("email") == 1

    	when: "we fill in all valid and unique info"
    	staff1.email = "staff@textup.org"

    	then:
    	staff1.validate() == true

    	when: "we try to create a staff with duplicate username"
    	mockForConstraintsTests(Staff, [staff1])
    	staff1.save(flush:true, failOnError:true)

    	Staff staff2 = new Staff(username:un, password:"password",
    		name:"Staff", email:"staff@textup.org", org:org)
    	staff2.personalPhoneAsString = "1112223333"

    	then: "usernames are NOT case sensitive"
    	staff2.validate() == false
    	staff2.errors.errorCount == 1
    	staff2.errors.getFieldErrorCount("username") == 1
    }

    void "test valid username"() {
        given: "a valid staff"
        assert s1.validate()

        when:
        s1.username = null

        then:
        !s1.validate()

        when:
        s1.username = ""

        then:
        !s1.validate()

        when:
        s1.username = "kiki bai"

        then:
        !s1.validate()

        when:
        s1.username = "kiki!!!123--hi"

        then:
        !s1.validate()

        when:
        s1.username = "kikiBAI_-=@,.;"

        then:
        s1.validate()
    }

    void "test converting to author"() {
        given: "a valid unsaved staff"
        Staff unsavedStaff = new Staff(username:"9StAf92", password:"password",
            name:"Name", org:org, personalPhoneAsString:"1112223333", email:"ok@ok.com")
        assert unsavedStaff.validate()

        when: "we convert saved and unsaved staff to author"
        Author unsavedAuth = unsavedStaff.toAuthor(),
            savedAuth = s1.toAuthor()

        then:
        unsavedAuth.id == null // staff is unsaved
        unsavedAuth.type == AuthorType.STAFF
        unsavedAuth.name == unsavedStaff.name

        savedAuth.id == s1.id
        savedAuth.type == AuthorType.STAFF
        savedAuth.name == s1.name
    }

    void "test getting phones"() {
        given: "phone"
        Phone ph = s1.phone

    	when: "phone is active"
        assert ph.isActive

        then:
        s1.hasInactivePhone == false
    	s1.phone == p1
        s1.phoneWithAnyStatus == p1
    	s1.allPhones.size() == 2
    	s1.allPhones.every {
    		it == p1 || it == tPh1
    	}

        when: "phone is inactive"
        ph.deactivate()
        ph.save(flush:true, failOnError:true)
        assert !ph.isActive

        then:
        s1.hasInactivePhone == true
        s1.phone == null
        s1.phoneWithAnyStatus == p1
        s1.allPhones.size() == 1
        s1.allPhones.every { it == tPh1 }
    }

    void "test operations on schedules"() {
    	given: "a valid staff"
    	Staff staff1 = new Staff(username:"12staff", password:"password",
    		name:"Staff", email:"staff@textup.org", org:org,
    		personalPhoneAsString:"1112223333")
    	staff1.save(flush:true, failOnError:true)

    	when: "manual schedule is off"
    	staff1.manualSchedule = false
    	staff1.isAvailable = true

    	LocalTime midnight = new LocalTime(0, 0)
		LocalTime t1 = new LocalTime(1, 0)
		DateTime availableTime = DateTime.now(DateTimeZone.UTC)
				.plusDays(1).withHourOfDay(0).withMinuteOfHour(30),
			tomMidnight = DateTime.now(DateTimeZone.UTC)
				.plusDays(1).withTimeAtStartOfDay(),
			tom1AM = DateTime.now(DateTimeZone.UTC)
				.plusDays(1).withTimeAtStartOfDay().plusHours(1)

    	String tomString = getDayOfWeekStringFor(DateTime.now(DateTimeZone.UTC).plusDays(1))
    	staff1.updateSchedule((tomString):[new LocalInterval(midnight, t1)])
    	staff1.save(flush:true, failOnError:true)

    	then: "we can ask all the schedule questions"
    	staff1.isAvailableNow() == false
		staff1.isAvailableAt(availableTime).payload == true
		staff1.nextChange().payload instanceof ScheduleChange
		staff1.nextChange().payload.type == ScheduleStatus.AVAILABLE
		staff1.nextChange().payload.when == tomMidnight
		staff1.nextAvailable().payload == tomMidnight
		staff1.nextUnavailable().payload == tom1AM

    	when: "manual schedule is on"
    	staff1.manualSchedule = true

    	then: "we can only ask about availability right now"
    	staff1.isAvailableNow() == staff1.isAvailable
    	staff1.isAvailableAt(availableTime).success == false
    	staff1.isAvailableAt(availableTime).payload.code == "staff.scheduleInfoUnavailable"
    	staff1.nextChange().success == false
    	staff1.nextChange().payload.code == "staff.scheduleInfoUnavailable"
    	staff1.nextAvailable().success == false
    	staff1.nextAvailable().payload.code == "staff.scheduleInfoUnavailable"
    	staff1.nextUnavailable().success == false
    	staff1.nextUnavailable().payload.code == "staff.scheduleInfoUnavailable"
    }

    protected String getDayOfWeekStringFor(DateTime dt) {
    	switch(dt.dayOfWeek) {
            case DateTimeConstants.SUNDAY: return "sunday"
            case DateTimeConstants.MONDAY: return "monday"
            case DateTimeConstants.TUESDAY: return "tuesday"
            case DateTimeConstants.WEDNESDAY: return "wednesday"
            case DateTimeConstants.THURSDAY: return "thursday"
            case DateTimeConstants.FRIDAY: return "friday"
            case DateTimeConstants.SATURDAY: return "saturday"
        }
    }

    void "test can share with"() {
    	expect:
    	s1.canShareWith.size() == 1
    	s1.canShareWith.every { it == s2 }
    }
}
