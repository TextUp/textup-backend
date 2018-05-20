package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.StaffStatus
import org.textup.util.CustomSpec
import spock.lang.Unroll

class PhoneJsonMarshallerIntegrationSpec extends CustomSpec {

    def _originalGetLoggedInAndActive

    def grailsApplication
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

    @Unroll
    void "test marshal phone when has notification policy is #description"() {
        given:
        Staff authUser
        if (isLoggedIn) {
            if (isOwner) {
                authUser = p1.owner.all[0]
            }
            else {
                authUser = s2
                assert p1.owner.all.contains(s2) == false
            }

            NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(authUser.id)
            np1.useStaffAvailability = true
            np1.save(flush:true, failOnError:true)
        }
        overrideGetLoggedInAndActiveWith({ authUser })

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(p1 as JSON) as Map
        }

        then:
        json.id == p1.id
        json.number == p1.number.e164PhoneNumber
        json.awayMessage == p1.awayMessage
        json.tags.size() == p1.tags.size()
        json.voice == p1.voice.toString()
        json.language == p1.language.toString()
        json.mandatoryEmergencyMessage == Constants.AWAY_EMERGENCY_MESSAGE
        p1.tags.every { ContactTag ct1 ->
            json.tags.find { it.id == ct1.id }
        }
        if (showAvailabilityInfo) {
            NotificationPolicy np1 = p1.owner.getPolicyForStaff(authUser.id)
            assert json.useStaffAvailability == true
            assert json.manualSchedule == np1.useStaffAvailability
            assert json.isAvailable == np1.useStaffAvailability
            assert json.isAvailableNow == s1.isAvailableNow()
            assert json.schedule == np1.schedule
            assert json.schedule == null // no schedule created yet
        }
        else {
            assert json.useStaffAvailability == null
            assert json.manualSchedule == null
            assert json.isAvailable == null
            assert json.isAvailableNow == null
            assert json.schedule == null
        }

        where:
        isLoggedIn | isOwner | showAvailabilityInfo | description
        false      | false   | false                | "not logged in"
        true       | false   | false                | "logged in but not one of phone's owners"
        true       | true    | true                 | "logged in, phone owner"
    }

    void "test marshal phone when no notification policy"() {
        given: "a new staff member owner for this phone"
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
            name: "Name",
            password: "password",
            email: "hello@its.me",
            org: org,
            manualSchedule: true,
            isAvailable: false)
        staff1.save(flush:true, failOnError:true)

        p1.updateOwner(staff1)
        p1.save(flush:true, failOnError:true)

        assert p1.owner.getPolicyForStaff(staff1.id) == null
        overrideGetLoggedInAndActiveWith({ staff1 })

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(p1 as JSON) as Map
        }

        then: "default to staff availability and do not sure policy-level availability info"
        json.id == p1.id
        json.number == p1.number.e164PhoneNumber
        json.awayMessage == p1.awayMessage
        json.tags.size() == p1.tags.size()
        json.voice == p1.voice.toString()
        json.language == p1.language.toString()
        json.mandatoryEmergencyMessage == Constants.AWAY_EMERGENCY_MESSAGE
        p1.tags.every { ContactTag ct1 ->
            json.tags.find { it.id == ct1.id }
        }
        json.useStaffAvailability == true
        json.isAvailableNow == staff1.isAvailableNow()
        // no policy-level availability info
        json.manualSchedule == null
        json.isAvailable == null
        json.schedule == null
    }
}
