package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.textup.rest.NotificationStatus
import org.textup.type.NotificationLevel
import spock.lang.Specification

@Domain(NotificationPolicy)
@TestMixin(HibernateTestMixin)
class NotificationPolicySpec extends Specification {

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
