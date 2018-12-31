package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import org.textup.rest.NotificationStatus
import org.textup.test.*
import org.textup.type.NotificationLevel

@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class NotificationPolicySpec extends CustomSpec {

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
    	when: "an empty notification policy"
    	NotificationPolicy np1 = new NotificationPolicy()

    	then: "invalid"
    	np1.validate() == false
    	np1.errors.errorCount == 1
    	np1.blacklistData == ""
    	np1.whitelistData == ""

    	when: "all filled out"
    	np1.staffId = 8L

    	then: "valid"
    	np1.validate() == true
    }

    void "test caching staff object"() {
        when: "we have a new notification policy and a staff"
        NotificationPolicy np1 = new NotificationPolicy(staffId:s1.id)
        assert np1.validate() == true

        then: "cached staff is null"
        np1._staff == null

        when: "we get the staff object"
        np1.getStaff()

        then: "cached staff is no longer null"
        np1._staff instanceof Staff
        np1._staff.id == s1.id
        np1.getStaff().id == s1.id

        when: "we update the staff id"
        np1.staffId = s2.id

        then: "cached staff is cleared"
        np1._staff == null

        when: "we get the staff object"
        np1.getStaff()

        then: "cached staff corresponds to the newly-updated id"
        np1._staff instanceof Staff
        np1._staff.id == s2.id
        np1.getStaff().id == s2.id
    }

    void "test availability on the policy"() {
        given: "a new notification policy not using staff availability"
        NotificationPolicy np1 = new NotificationPolicy(staffId: s1.id,
            useStaffAvailability: false)
        np1.save(flush:true, failOnError:true)
        s1.manualSchedule = true
        s1.isAvailable = true
        s1.save(flush:true, failOnError:true)

        int sBaseline = Schedule.count()

        when: "we update manual availability on policy"
        np1.manualSchedule = true
        np1.isAvailable = false

        then: "availability methods ignore staff availability value"
        np1.policyIsAvailableNow() == false
        np1.isAvailableNow() == false
        s1.isAvailableNow() == true

        when: "we switch to schedule-based availability on policy"
        np1.manualSchedule = false
        np1.isAvailable = false
        assert np1.updateSchedule([
            monday:["0000:2359"],
            tuesday:["0000:2359"],
            wednesday:["0000:2359"],
            thursday:["0000:2359"],
            friday:["0000:2359"],
            saturday:["0000:2359"],
            sunday:["0000:2359"]
        ], "Etc/UTC").success
        np1.save(flush:true, failOnError:true)

        s1.isAvailable = false
        s1.save(flush:true, failOnError:true)

        then: "new schedule is created for policy and staff availability is ignored"
        Schedule.count() == sBaseline + 1
        np1.isAvailable == false
        np1.policyIsAvailableNow() == true
        np1.isAvailableNow() == true
        s1.isAvailableNow() == false
    }

    void "test availability proxied to the staff"() {
        when: "a new notification policy using staff availability"
        NotificationPolicy np1 = new NotificationPolicy(staffId: s1.id,
            useStaffAvailability: true, manualSchedule: true, isAvailable: false)
        np1.save(flush:true, failOnError:true)
        s1.manualSchedule = true
        s1.isAvailable = true
        s1.save(flush:true, failOnError:true)

        then: "policy-level availability is ignored"
        np1.useStaffAvailability == true
        np1.isAvailable == false
        np1.policyIsAvailableNow() == false
        np1.isAvailableNow() == true
        s1.isAvailableNow() == true
    }

    void "manipulating lists"() {
    	when: "we have a new notification policy"
    	NotificationPolicy np1 = new NotificationPolicy(staffId:88L)
    	assert np1.validate() == true

    	then: "lists not initialized until we call addTo*, removeFrom*, isIn* methods"
    	np1._blacklist == null
    	np1._whitelist == null

    	when: "adding to lists"
    	Long recId = 2L
    	assert np1.addToBlacklist(recId) == true
    	assert np1.addToWhitelist(recId) == true

    	then:
    	np1._blacklist instanceof HashSet
    	np1._whitelist instanceof HashSet
    	np1._blacklist.size() == 1
    	np1._whitelist.size() == 1
    	np1.isInBlacklist(recId) == true
    	np1.isInWhitelist(recId) == true
    	// not written until when we call validate
    	np1.blacklistData == ""
    	np1.whitelistData == ""

    	when: "removing from lists"
    	assert np1.removeFromBlacklist(recId) == true
    	assert np1.removeFromWhitelist(recId) == true

    	then:
    	np1._blacklist instanceof HashSet
    	np1._whitelist instanceof HashSet
    	np1._blacklist.size() == 0
    	np1._whitelist.size() == 0
    	np1.isInBlacklist(recId) == false
    	np1.isInWhitelist(recId) == false
    	// not written until when we call validate
    	np1.blacklistData == ""
    	np1.whitelistData == ""

    	when: "write persisted data fields after calling validate"
    	assert np1.addToBlacklist(recId) == true
    	assert np1.addToWhitelist(recId) == true

    	assert np1.validate() == true

    	then:
    	np1._blacklist instanceof HashSet
    	np1._whitelist instanceof HashSet
    	np1._blacklist.size() == 1
    	np1._whitelist.size() == 1
    	np1.isInBlacklist(recId) == true
    	np1.isInWhitelist(recId) == true
    	// not written until when we call validate
    	np1.blacklistData != null
    	np1.whitelistData != null
    }

    void "test enabling and disabling notifications"() {
    	given: "two notification policies with different default levels"
    	NotificationPolicy allPol = new NotificationPolicy(staffId:88L, level:NotificationLevel.ALL)
    	NotificationPolicy nonePol = new NotificationPolicy(staffId:88L, level:NotificationLevel.NONE)

    	assert allPol.validate() == true
    	assert nonePol.validate() == true

    	when: "testing to see if can notify"
    	Long recId = 2L

    	then:
    	allPol.canNotify(recId) == true
    	nonePol.canNotify(recId) == false

    	when: "disabling"
    	[allPol, nonePol]*.disable(recId)

    	then:
    	[allPol, nonePol].each { NotificationPolicy pol ->
	    	assert pol.canNotify(recId) == false
	    	assert pol.canNotifyForAny([recId]) == false
	    	assert pol._blacklist.isEmpty() == false
	    	assert pol._blacklist.contains(recId) == true
	    	assert pol._whitelist.isEmpty() == true
    	}

    	when: "enabling"
    	[allPol, nonePol]*.enable(recId)

    	then:
    	[allPol, nonePol].each { NotificationPolicy pol ->
	    	assert pol.canNotify(recId) == true
	    	assert pol.canNotifyForAny([recId]) == true
	    	assert pol._blacklist.isEmpty() == true
			assert pol._whitelist.isEmpty() == false
	    	assert pol._whitelist.contains(recId) == true
    	}
    }
}
