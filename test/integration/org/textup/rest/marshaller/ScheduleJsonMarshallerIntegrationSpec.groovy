package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.util.*

class ScheduleJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling schedule"() {
        given:
        Schedule sched1 = s1.schedule
        sched1.updateWithIntervalStrings([
            monday:["0130:0231", "0230:0330", "0400:0430"]
        ])
        sched1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(sched1 as JSON)
        }

    	then:
    	json.id == sched1.id
        json.isAvailableNow == sched1.isAvailableNow()
        json.nextUnavailable != null
        json.nextAvailable != null
        json.monday == ["0130:0330", "0400:0430"]
    }
}
