package org.textup.rest

import grails.plugins.rest.client.RestResponse
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.Constants
import org.textup.util.*
import static org.springframework.http.HttpStatus.*
import org.textup.validator.EmailEntity

// Note: The org relation on Staff must be lazy:false because the Staff object
// somehow is detached from the session by the time it reaches the JSON marshaller
// on some instances of the log-in operation

class SignupFunctionalSpec extends RestSpec {

    def setup() {
        setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    void "test signing up with a new organization"() {
        given:
        String authToken = getAuthToken()

        when: "create new staff and organization"
        String n = "Kiki Bai"
        String em = "kiki@kiki.com"
        String un = "specialstaff123${iterationCount}"
        String code = Constants.DEFAULT_LOCK_CODE
        String pwd = "password"
        String orgN = "Kiki's Organization"
        String add = "1234 Kiki Road"
        int latitude = 34, longitude = 22
        RestResponse response = rest.post("$baseUrl/v1/public/staff") {
            contentType("application/json")
            json {
                staff {
                    name = n
                    email = em
                    username = un
                    password = pwd
                    lockCode = code
                    org {
                        name = orgN
                        location {
                            address = add
                            lat = latitude
                            lon = longitude
                        }
                    }
                }
            }
        }

        then:
        response.status == CREATED.value()
        response.json.staff.name == n
        response.json.staff.email == em
        response.json.staff.username == un
        response.json.staff.password == null
        response.json.staff.status == StaffStatus.ADMIN.toString()
        response.json.staff.org == null // because not logged in

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
        response.status == OK.value()
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
        response.json.roles.contains("ROLE_USER") == true

        when: "view org we just created"
        Long orgId = response.json.staff.org.id
        String newAuthToken = response.json.access_token
        response = rest.get("$baseUrl/v1/organizations/${orgId}") {
            header("Authorization", "Bearer $newAuthToken")
        }

        then:
        response.status == OK.value()
        response.json.organization.id == orgId
        response.json.organization.name instanceof String
        response.json.organization.location.address == add
        response.json.organization.location.lat == latitude
        response.json.organization.location.lon == longitude
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
        response.status == OK.value()
        response.json.organizations instanceof List
        response.json.organizations.links != null
        response.json.meta != null

        when: "pick an organization to sign up with"
        String n = "Kiki Bai"
        String em = "kiki@kiki.com"
        String un = "specialstaff123${iterationCount}"
        String pwd = "password"
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
        response.status == CREATED.value()
        response.json.staff.name == n
        response.json.staff.email == em
        response.json.staff.username == un
        response.json.staff.password == null
        response.json.staff.status == StaffStatus.PENDING.toString()
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
        response.status == OK.value()
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
        response.json.roles.contains("ROLE_USER") == true

        when: "view org we just created"
        Long orgId = response.json.staff.org.id
        String newAuthToken = response.json.access_token
        response = rest.get("$baseUrl/v1/organizations/${orgId}") {
            header("Authorization", "Bearer $newAuthToken")
        }

        then:
        response.status == OK.value()
        response.json.organization.id == orgId
        response.json.organization.status == existingOrg.status
        response.json.organization.numAdmins == existingOrg.numAdmins
        response.json.organization.links != null
    }
}
