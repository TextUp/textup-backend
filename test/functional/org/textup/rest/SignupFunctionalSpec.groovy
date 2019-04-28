package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.*
import grails.test.mixin.support.*
import java.util.concurrent.*
import javax.servlet.http.HttpServletRequest
import org.joda.time.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.cache.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin) // enables local use of validator classes
class SignupFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        doSetup()
    }

    def cleanup() {
    	doCleanup()
    }

    void "test signing up with a new organization"() {
        given:
        String authToken = getAuthToken()
        String n = TestUtils.randString()
        String em = TestUtils.randEmail()
        String thisUn = TestUtils.randString()
        String code = Constants.DEFAULT_LOCK_CODE
        String thisPwd = TestUtils.randString()
        String orgN = TestUtils.randString()
        String add = TestUtils.randString()
        int latitude = TestUtils.randIntegerUpTo(90)
        int longitude = TestUtils.randIntegerUpTo(90)

        when: "create new staff and organization"
        RestResponse response = rest.post("$baseUrl/v1/public/staff") {
            contentType("application/json")
            json {
                staff {
                    name = n
                    email = em
                    username = thisUn
                    password = thisPwd
                    lockCode = code
                    org {
                        name = orgN
                        location {
                            address = add
                            lat = latitude
                            lng = longitude
                        }
                    }
                }
            }
        }

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json.staff.name == n
        response.json.staff.username == thisUn
        response.json.staff.password == null
        response.json.staff.status == StaffStatus.ADMIN.toString()
        response.json.staff.email == null // because not logged in
        response.json.staff.org == null // because not logged in

        when: "log in with new user"
        // Allow time for the session to flush before trying to log in
        TimeUnit.SECONDS.sleep(3)
        Long staffId = response.json.staff.id
        response = rest.post("$baseUrl/login") {
            contentType "application/json"
            json {
                username = thisUn
                password = thisPwd
            }
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.staff instanceof Map
        response.json.staff.id == staffId
        response.json.staff.name == n
        response.json.staff.email == em
        response.json.staff.status == StaffStatus.ADMIN.toString()
        response.json.staff.org instanceof Map
        response.json.staff.org.name == orgN
        // should be PENDING but we are not logged in so cannot see this info
        response.json.staff.org.status == null
        response.json.roles instanceof List
        response.json.roles.isEmpty() == false
        response.json.roles.contains("ROLE_NO_ROLES") == false
        response.json.roles.contains("ROLE_USER")

        when: "view org we just created"
        Long orgId = response.json.staff.org.id
        String newAuthToken = response.json.access_token
        response = rest.get("$baseUrl/v1/organizations/${orgId}") {
            header("Authorization", "Bearer $newAuthToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.organization.id == orgId
        response.json.organization.name instanceof String
        response.json.organization.location.address == add
        response.json.organization.location.lat == latitude
        response.json.organization.location.lng == longitude
        response.json.organization.links != null
        // STILL cannot see authenticated information because, by definition, the staff member
        // must be either STAFF or ADMIN and be at an approved org to be active. Since this org
        // is not approved, we are not active even though we are logged in
        response.json.organization.status == null
        response.json.organization.teams == null
        response.json.organization.numAdmins == null
        response.json.organization.timeout == null
    }

    void "test signup with existing organization"() {
        given:
        String authToken = getAuthToken()

        when: "list organiations that are publicly viewable"
        RestResponse response = rest.get("$baseUrl/v1/public/organizations")

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.organizations instanceof List
        response.json.organizations.links != null
        response.json.meta != null

        when: "pick an organization to sign up with"
        String n = TestUtils.randString()
        String em = TestUtils.randEmail()
        String un = TestUtils.randString()
        String pwd = TestUtils.randString()
        String code = Constants.DEFAULT_LOCK_CODE
        Map existingOrg = response.json.organizations.find { it.id != null }
        response = rest.post("$baseUrl/v1/public/staff") {
            contentType("application/json")
            json {
                staff {
                    name = n
                    email = em
                    username = un
                    password = pwd
                    lockCode = code
                    org {
                        id = existingOrg.id
                    }
                }
            }
        }

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json.staff.name == n
        response.json.staff.username == un
        response.json.staff.password == null
        response.json.staff.status == StaffStatus.PENDING.toString()
        response.json.staff.email == null // not logged in
        response.json.staff.org == null // not logged in

        when: "log in with new user"
        Long staffId = response.json.staff.id
        response = rest.post("$baseUrl/login") {
            contentType "application/json"
            json {
                username = un
                password = pwd
            }
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.staff instanceof Map
        response.json.staff.id == staffId
        response.json.staff.name == n
        response.json.staff.email == em
        response.json.staff.status == StaffStatus.PENDING.toString()
        response.json.staff.org instanceof Map
        response.json.staff.org.name == existingOrg.name
        response.json.staff.org.status == existingOrg.status
        response.json.roles instanceof List
        response.json.roles.isEmpty() == false
        response.json.roles.contains("ROLE_NO_ROLES") == false
        response.json.roles.contains("ROLE_USER")

        when: "view org we just created"
        Long orgId = response.json.staff.org.id
        String newAuthToken = response.json.access_token
        response = rest.get("$baseUrl/v1/organizations/${orgId}") {
            header("Authorization", "Bearer $newAuthToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.organization.id == orgId
        response.json.organization.status == existingOrg.status
        response.json.organization.numAdmins == existingOrg.numAdmins
        response.json.organization.links != null
    }
}
