package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.StaffStatus
import org.textup.util.CustomSpec

class OrganizationJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication
    def _originalGetLoggedInAndActive

    AuthService authService

    def setup() {
    	setupIntegrationData()
        authService = grailsApplication.mainContext.getBean('authService')
    }

    def cleanup() {
    	cleanupIntegrationData()
        // restore overriden methods
        if (_originalGetLoggedInAndActive) {
            authService.metaClass.getLoggedInAndActive = _originalGetLoggedInAndActive
            _originalGetLoggedInAndActive = null
        }
    }

    protected void overrideGetLoggedInAndActiveWith(Closure closure) {
        if (!_originalGetLoggedInAndActive) {
            _originalGetLoggedInAndActive = authService.metaClass.getLoggedInAndActive
        }
        authService.metaClass.getLoggedInAndActive = closure
    }

    void "test marshal organization when not logged in"() {
    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(org as JSON) as Map
    	}

    	then:
    	json.id == org.id
    	json.name == org.name
    	json.location instanceof Map
    	json.location.address == org.location.address
    	json.location.lat == org.location.lat
    	json.location.lon == org.location.lon
        json.timeout == null
        json.status == null
        json.numAdmins == null
    	json.teams == null
    }

    void "test marshal organization when active user but NOT a member of the organization"() {
        given:
        otherS1.status = StaffStatus.ADMIN
        otherS1.save(flush:true, failOnError:true)

        overrideGetLoggedInAndActiveWith({ otherS1 })
        assert otherS1.org.id != org.id
        assert otherS1.status == StaffStatus.STAFF || otherS1.status == StaffStatus.ADMIN

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(org as JSON) as Map
        }

        then:
        json.id == org.id
        json.name == org.name
        json.location instanceof Map
        json.location.address == org.location.address
        json.location.lat == org.location.lat
        json.location.lon == org.location.lon
        json.timeout == null
        json.status == null
        json.numAdmins == null
        json.teams == null
    }

    void "test marshal organization when active user that is a member of this organization"() {
        given:
        overrideGetLoggedInAndActiveWith({ s1 })
        assert s1.org.id == org.id
        assert s1.status == StaffStatus.STAFF || s1.status == StaffStatus.ADMIN

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
