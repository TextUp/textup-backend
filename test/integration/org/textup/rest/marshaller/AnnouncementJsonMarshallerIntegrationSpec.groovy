package org.textup.rest.marshaller

import grails.converters.JSON
import org.joda.time.DateTime
import org.textup.util.CustomSpec
import org.textup.*

class AnnouncementJsonMarshallerIntegrationSpec extends CustomSpec {

	def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling announcement"() {
        given:
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"hello", expiresAt:DateTime.now().plusDays(1))
        announce.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(announce as JSON) as Map
    	}

    	then:
    	json.id == announce.id
        json.id == announce.id
        json.isExpired == announce.isExpired
        json.expiresAt == announce.expiresAt.toString()
        json.message == announce.message
        json.whenCreated == announce.whenCreated.toString()
        json.numReceipts == announce.numReceipts
        json.numCallReceipts == announce.numCallReceipts
        json.numTextReceipts == announce.numTextReceipts
        json.staff == announce.owner.owner.ownerId
        json.team == null
    }
}
