package org.textup.rest.marshallers

import org.textup.util.CustomSpec
import grails.converters.JSON
import org.textup.*

class ContactableJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    protected boolean validate(Map json, Contactable c1) {
        assert json.id == c1.contactId
        assert json.lastRecordActivity == c1.lastRecordActivity.toString()
        assert json.name == c1.name
        assert json.note == c1.note
        assert json.status == c1.status.toString()
        assert json.numbers instanceof List
        assert json.numbers.size() == (c1.numbers ? c1.numbers.size() : 0)
        c1.numbers?.each { ContactNumber num ->
            assert json.numbers.find { it.number == num.prettyPhoneNumber }
        }
        true
    }

    void "test marshalling contact"() {
        given:
        IncomingSession sess1 = new IncomingSession(phone:c1.phone,
            numberAsString:"1112223333")
        sess1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(c1 as JSON) as Map
    	}

    	then:
        validate(json, c1)
        json.tags instanceof List
        json.tags.size() == c1.tags.size()
        c1.tags.every { ContactTag ct1 -> json.tags.find { it.id == ct1.id } }
        json.sessions instanceof List
        json.sessions.size() == c1.sessions.size()
        c1.sessions.every { IncomingSession session ->
            json.sessions.find { it.id == session.id }
        }
        json.sharedWith instanceof List
        json.sharedWith.size() == c1.sharedContacts.size()
        c1.sharedContacts.every { SharedContact sc1 ->
            json.sharedWith.find { it.id == sc1.id }
        }
    }

    void "test marshalling shared contact"() {
        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(sc2 as JSON) as Map
        }

        then:
        validate(json, sc2)
        json.permission == sc2.permission.toString()
        json.startedSharing == sc2.whenCreated.toString()
        json.sharedBy == sc2.sharedBy.name
        json.sharedById == sc2.sharedBy.id
    }
}
