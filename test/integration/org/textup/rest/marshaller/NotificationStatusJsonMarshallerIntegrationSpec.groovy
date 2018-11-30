package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.test.*
import org.textup.*
import org.textup.rest.NotificationStatus
import org.textup.util.*

class NotificationStatusJsonMarshallerIntegrationSpec extends CustomSpec {

	def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling notification"() {
    	given: "a notification status"
    	NotificationStatus stat1 = new NotificationStatus(staff:s1, canNotify:true, isAvailableNow:true)
        assert stat1.validate()

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = TestUtils.jsonToMap(stat1 as JSON)
    	}

    	then:
    	json.id == stat1.staff.id
    	json.name == stat1.staff.name
    	json.username == stat1.staff.username
    	json.canNotify == stat1.canNotify
        // no longer include phone number to contact. See corresponding class for details
        json.number == null
	}
}
