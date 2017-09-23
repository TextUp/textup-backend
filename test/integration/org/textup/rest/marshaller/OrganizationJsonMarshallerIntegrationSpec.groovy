package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.StaffStatus
import org.textup.util.CustomSpec

class OrganizationJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshal organization"() {
    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(org as JSON) as Map
    	}

    	then:
    	json.id == org.id
    	json.name == org.name
        json.timeout == org.timeout
        json.status == org.status.toString()
    	json.numAdmins == org.countPeople(statuses:[StaffStatus.ADMIN])
    	json.location instanceof Map
    	json.location.address == org.location.address
    	json.location.lat == org.location.lat
    	json.location.lon == org.location.lon
    	json.teams instanceof List
    	json.teams.size() == org.teams.size()
    	org.teams.every { Team team -> json.teams.find { it.id == team.id } }
    }
}
