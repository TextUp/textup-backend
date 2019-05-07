package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class StaffJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling staff without permission"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone(s1)

        MockedMethod isAllowed = MockedMethod.create(Staffs, "isAllowed") {
            Result.createError([], ResultStatus.FORBIDDEN)
        }

        when:
        Map json = TestUtils.objToJsonMap(s1)

        then:
        json.id == s1.id
        json.name == s1.name
        json.phone instanceof Map
        json.phone.id == p1.id
        json.status == s1.status.toString()
        json.username == s1.username
        json.channelName == SocketUtils.channelName(s1)
        json.email == null
        json.org == null
        json.personalNumber == null
        json.teams == null

        cleanup:
        isAllowed?.restore()
    }

    void "test marshalling staff with inactive phone"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildStaffPhone(s1)

        when:
        Map json = TestUtils.objToJsonMap(s1)

        then: "still show"
        p1.isActive() == false
        json.phone instanceof Map
        json.phone.id == p1.id
    }

    void "test marshalling staff with permission"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone(s1)

        MockedMethod isAllowed = MockedMethod.create(Staffs, "isAllowed") { Result.void() }

        when:
        Map json = TestUtils.objToJsonMap(s1)

        then:
        json.id == s1.id
        json.name == s1.name
        json.phone instanceof Map
        json.phone.id == p1.id
        json.status == s1.status.toString()
        json.username == s1.username
        json.channelName == SocketUtils.channelName(s1)
        json.email == s1.email
        json.org instanceof Map
        json.org.id == s1.org.id
        json.personalNumber == s1.personalNumber.prettyPhoneNumber
        json.teams instanceof Collection
        json.teams.isEmpty()

        when: "clear personal number"
        s1.personalNumberAsString = null
        json = TestUtils.objToJsonMap(s1)

        then:
        json.personalNumber == null

        cleanup:
        isAllowed?.restore()
    }
}
