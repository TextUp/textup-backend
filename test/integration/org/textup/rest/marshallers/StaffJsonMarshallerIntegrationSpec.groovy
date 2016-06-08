package org.textup.rest.marshallers

import org.textup.util.CustomSpec
import grails.converters.JSON
import org.textup.*

class StaffJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    protected boolean validate(Map json, Staff s1) {
        assert json.id == s1.id
        assert json.name == s1.name
        assert json.username == s1.username
        assert json.email == s1.email
        assert json.status == s1.status.toString()
        assert json.manualSchedule == s1.manualSchedule
        assert json.schedule instanceof Map
        assert json.phone instanceof Map
        true
    }

    void "test marshalling staff, weekly schedule, not logged in"() {
        given:
        s1.manualSchedule = false
        s1.schedule.updateWithIntervalStrings([
            monday:["0130:0231", "0230:0330", "0400:0430"]
        ])
        s1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(s1 as JSON) as Map
    	}

    	then:
    	validate(json, s1)
        json.isAvailable == null
    }

    void "test marshalling staff, manual schedule, not logged in"() {
        given:
        s1.manualSchedule = true
        s1.isAvailable = true
        s1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(s1 as JSON) as Map
        }

        then:
        validate(json, s1)
        json.isAvailable != null
    }
}
