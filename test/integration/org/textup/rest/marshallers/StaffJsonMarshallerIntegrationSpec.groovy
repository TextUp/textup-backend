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
        assert json.org == s1.org.id
        assert json.orgName == s1.org.name
        assert json.awayMessage == s1.phone.awayMessage
        assert json.phone == s1.phone.number.e164PhoneNumber
        assert json.personalPhoneNumber == s1.personalPhoneNumber.e164PhoneNumber
        assert json.manualSchedule == s1.manualSchedule
        assert json.isAvailableNow == s1.isAvailableNow()
        assert json.tags instanceof List
        assert json.tags.size() == s1.phone.tags.size()
        assert json.teams instanceof List
        assert json.teams.size() == s1.teams.size()
        assert json.schedule instanceof Map
        assert Constants.DAYS_OF_WEEK.every { json.schedule[it] instanceof List }
        true
    }

    void "test marshalling staff, weekly schedule"() {
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
        json.schedule.nextAvailable != null
        json.schedule.nextUnavailable != null
        json.isAvailable == null
    }

    void "test marshalling staff, manual schedule"() {
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
        json.schedule.nextAvailable == null
        json.schedule.nextUnavailable == null
        json.isAvailable != null
    }
}
