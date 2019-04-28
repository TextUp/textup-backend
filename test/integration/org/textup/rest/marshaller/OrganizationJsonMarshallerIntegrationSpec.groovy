package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class OrganizationJsonMarshallerIntegrationSpec extends Specification {

    void "test marshal organization when not logged in"() {
        given:
        Organization org1 = TestUtils.buildOrg()

        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createError([], ResultStatus.FORBIDDEN)
        }

    	when:
    	Map json = TestUtils.objToJsonMap(org1)

    	then:
        json.size() == 4
    	json.id == org1.id
    	json.name == org1.name
    	json.location instanceof Map
        json.links instanceof Map

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test marshal organization when active user but NOT a member of the organization"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Staff s1 = TestUtils.buildStaff(org1)
        Staff s2 = TestUtils.buildStaff()
        s1.status = StaffStatus.ADMIN
        Organization.withSession { it.flush() }

        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s2)
        }

        when:
        Map json = TestUtils.objToJsonMap(org1)

        then:
        json.size() == 4
        json.id == org1.id
        json.name == org1.name
        json.location instanceof Map
        json.links instanceof Map

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test marshal organization when active user that is a member of this organization"() {
        given:
        Organization org1 = TestUtils.buildOrg()
        Staff s1 = TestUtils.buildStaff(org1)
        Staff s2 = TestUtils.buildStaff(org1)
        s2.status = StaffStatus.ADMIN
        Team t1 = TestUtils.buildTeam(org1)

        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }

        when:
        Map json = TestUtils.objToJsonMap(org1)

        then:
        json.id == org1.id
        json.name == org1.name
        json.status == org1.status.toString()
        json.numAdmins == 1
        json.location instanceof Map
        json.teams instanceof List
        json.teams.size() == 1
        json.teams[0].id == t1.id

        json.timeout == org1.timeout
        json.awayMessageSuffix == org1.awayMessageSuffix
        json.timeoutMin == 0
        json.timeoutMax == ValidationUtils.MAX_LOCK_TIMEOUT_MILLIS
        json.awayMessageSuffixMaxLength == ValidationUtils.TEXT_BODY_LENGTH - 1

        cleanup:
        tryGetActiveAuthUser?.restore()
    }
}
