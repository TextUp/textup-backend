package org.textup.rest.marshallers

import grails.converters.JSON
import org.textup.*
import org.textup.types.PhoneOwnershipType
import org.textup.util.CustomSpec
import org.textup.validator.Notification

class NotificationJsonMarshallerIntegrationSpec extends CustomSpec {

	def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling notification"() {
    	given: "notification"
    	String contents = "hi"
        Boolean isOutgoing = true
    	Notification notif = new Notification(owner:p1.owner, record:tag1.record,
    		contents:contents, outgoing:isOutgoing, tokenId:1L)
    	assert notif.validate() == true

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(notif as JSON) as Map
    	}

    	then:
        json.id == notif.tokenId
        json.ownerType ==
    		(p1.owner.type == PhoneOwnershipType.INDIVIDUAL ? "staff" : "team")
		json.ownerId == (p1.owner.type == PhoneOwnershipType.INDIVIDUAL ?
            Staff.get(p1.owner.ownerId).username : p1.owner.name)
		json.ownerName == p1.owner.name
		json.ownerNumber == p1.number.e164PhoneNumber
		json.contents == contents
        json.outgoing == isOutgoing
		json.otherType == "tag"
		json.otherId == tag1.name
		json.otherName == tag1.name
    }
}
