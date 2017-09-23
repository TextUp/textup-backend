package org.textup.rest.marshaller

import org.textup.util.CustomSpec
import grails.converters.JSON
import org.textup.*

class SessionJsonMarshallerIntegrationSpec extends CustomSpec {

	def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling session"() {
        given:
        IncomingSession sess1 = new IncomingSession(phone:p1,
            numberAsString:"1112223333")
        sess1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(sess1 as JSON) as Map
    	}

    	then:
    	json.id == sess1.id
        json.isSubscribedToText == sess1.isSubscribedToText
        json.isSubscribedToCall == sess1.isSubscribedToCall
        json.number == sess1.number.e164PhoneNumber
        json.whenCreated == sess1.whenCreated.toString()
        json.lastSentInstructions == sess1.lastSentInstructions.toString()
        json.shouldSendInstructions == sess1.shouldSendInstructions
        json.staff == sess1.phone.owner.ownerId
        json.team == null
    }
}
