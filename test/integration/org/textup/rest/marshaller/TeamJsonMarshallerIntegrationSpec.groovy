package org.textup.rest.marshaller

import org.textup.util.CustomSpec
import grails.converters.JSON
import org.textup.*

class TeamJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling team"() {
    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(t1 as JSON) as Map
    	}

    	then:
    	json.id == t1.id
    	json.name == t1.name
    	json.hexColor == t1.hexColor
        json.org == t1.org.id
        json.phone instanceof Map
        json.hasInactivePhone == t1.hasInactivePhone
    	json.location instanceof Map
    	json.numMembers == t1.activeMembers.size()
    }

    void "test marshalling team with inactive phone"() {
        given:
        t1.phone.deactivate()
        t1.phone.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(t1 as JSON) as Map
        }

        then:
        json.id == t1.id
        json.name == t1.name
        json.hexColor == t1.hexColor
        json.org == t1.org.id
        json.phone == null
        json.hasInactivePhone == t1.hasInactivePhone
        json.location instanceof Map
        json.numMembers == t1.activeMembers.size()
    }

    void "test marshalling team without phone"() {
        given: "team without phone"
        Team team1 = new Team(name:"UniqueTeam1", org:org)
        team1.location = new Location(address:"Testing Address", lat:0G, lon:1G)
        team1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(team1 as JSON) as Map
        }

        then:
        json.id == team1.id
        json.name == team1.name
        json.hexColor == team1.hexColor
        json.org == team1.org.id
        json.phone == null
        json.hasInactivePhone == t1.hasInactivePhone
        json.location instanceof Map
        json.numMembers == team1.activeMembers.size()
    }
}
