package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.rest.StaffPolicyAvailability
import org.textup.Schedule
import org.textup.util.*

class StaffPolicyAvailabilityJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
        setupIntegrationData()
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test marshalling staff policy availability"() {
        given: "a staff policy availability"
        // for purposes of testing, manually set properties
        StaffPolicyAvailability spa1 = new StaffPolicyAvailability(null, null, false)
        spa1.jointId = UUID.randomUUID().toString()
        spa1.name = UUID.randomUUID().toString()
        spa1.useStaffAvailability = false
        spa1.manualSchedule = false
        spa1.isAvailable = true
        spa1.isAvailableNow = true
        spa1.schedule = new Schedule()
        spa1.schedule.id = -88L

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(spa1 as JSON)
        }

        then:
        json.id == spa1.jointId
        json.name == spa1.name
        json.useStaffAvailability == spa1.useStaffAvailability
        json.manualSchedule == spa1.manualSchedule
        json.isAvailable == spa1.isAvailable
        json.isAvailableNow == spa1.isAvailableNow
        json.schedule.id == spa1.schedule.id
    }
}
