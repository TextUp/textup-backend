package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.test.*
import org.textup.type.StaffStatus
import org.textup.util.*

// TODO

class StaffJsonMarshallerIntegrationSpec extends CustomSpec {

    // def grailsApplication
    // AuthService authService
    // def _originalIsLoggedIn
    // def _originalIsAdminAtSameOrgAs

    // def setup() {
    //     setupIntegrationData()
    //     authService = grailsApplication.mainContext.getBean('authService')
    // }

    // def cleanup() {
    //     cleanupIntegrationData()
    //     // restore overridden methods
    //     if (_originalIsLoggedIn) {
    //         authService.metaClass.isLoggedIn = _originalIsLoggedIn
    //         _originalIsLoggedIn = null
    //     }
    //     if (_originalIsAdminAtSameOrgAs) {
    //         authService.metaClass.isAdminAtSameOrgAs = _originalIsAdminAtSameOrgAs
    //         _originalIsAdminAtSameOrgAs = null
    //     }
    // }

    // protected boolean validate(Map json, Staff s1) {
    //     assert json.id == s1.id
    //     assert json.name == s1.name
    //     assert json.username == s1.username
    //     assert json.email == s1.email
    //     assert json.status == s1.status.toString()
    //     assert json.manualSchedule == s1.manualSchedule
    //     assert json.schedule instanceof Map
    //     assert json.hasInactivePhone == s1.hasInactivePhone
    //     true
    // }

    // void "test marshalling staff, weekly schedule, not logged in"() {
    //     given:
    //     s1.manualSchedule = false
    //     s1.schedule.updateWithIntervalStrings([
    //         monday:["0130:0231", "0230:0330", "0400:0430"]
    //     ])
    //     s1.save(flush:true, failOnError:true)

    // 	when:
    // 	Map json
    // 	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    // 		json = TestUtils.jsonToMap(s1 as JSON)
    // 	}

    // 	then:
    // 	validate(json, s1)
    //     json.phone instanceof Map
    //     json.isAvailable == null
    // }

    // void "test marshalling staff, manual schedule, not logged in"() {
    //     given:
    //     s1.manualSchedule = true
    //     s1.isAvailable = true
    //     s1.save(flush:true, failOnError:true)

    //     when:
    //     Map json
    //     JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    //         json = TestUtils.jsonToMap(s1 as JSON)
    //     }

    //     then:
    //     validate(json, s1)
    //     json.phone instanceof Map
    //     json.isAvailable != null
    // }

    // void "test marshalling staff with inactive phone, not logged in"() {
    //     given:
    //     s1.phone.deactivate()
    //     s1.phone.save(flush:true, failOnError:true)

    //     when:
    //     Map json
    //     JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    //         json = TestUtils.jsonToMap(s1 as JSON)
    //     }

    //     then:
    //     validate(json, s1)
    //     json.phone == null
    // }

    // void "test marshalling when is admin at organization"() {
    //     given:
    //     s1.manualSchedule = true
    //     s1.isAvailable = true
    //     s1.phone.deactivate()
    //     s1.save(flush:true, failOnError:true)

    //     _originalIsLoggedIn = authService.metaClass.isLoggedIn
    //     _originalIsAdminAtSameOrgAs = authService.metaClass.isAdminAtSameOrgAs
    //     authService.metaClass.isLoggedIn = { Long id -> true }
    //     authService.metaClass.isAdminAtSameOrgAs = { Long id -> true }

    //     when:
    //     Map json
    //     JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    //         json = TestUtils.jsonToMap(s1 as JSON)
    //     }

    //     then:
    //     validate(json, s1)
    //     json.isAvailable != null
    //     json.phone == null
    //     json.org instanceof Map
    //     json.org.id == s1.org.id
    //     json.personalPhoneNumber == s1.personalPhoneNumber.e164PhoneNumber
    //     json.teams instanceof List
    //     json.teams.size() == s1.getTeams().size()
    //     s1.getTeams().every { Team team -> json.teams.find { it.id == team.id } }
    // }
}
