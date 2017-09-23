package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.rest.NotificationStatus
import org.textup.util.CustomSpec

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
    	NotificationStatus stat1 = new NotificationStatus(staff:s1, canNotify:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(stat1 as JSON) as Map
    	}

    	then:
    	json.id == stat1.staff.id
    	json.name == stat1.staff.name
    	json.username == stat1.staff.username
    	json.number == stat1.staff.phone.number.e164PhoneNumber
    	json.canNotify == stat1.canNotify
	}
}