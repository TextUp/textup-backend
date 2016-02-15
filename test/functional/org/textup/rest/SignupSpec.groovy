package org.textup.rest

import grails.plugins.rest.client.RestResponse
import org.textup.types.OrgStatus
import org.textup.types.StaffStatus
import org.textup.util.*
import static org.springframework.http.HttpStatus.*
import org.textup.validator.EmailEntity

class SignupSpec extends RestSpec {

    def setup() {
        setupData()
        // mock mail sending
        remote.exec({
            ctx.mailService.metaClass.sendMail { EmailEntity to, EmailEntity from, String subject,
                String contents, String templateId=null ->
                ctx.resultFactory.success()
            }
            return
        })
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
        response.json.staff.orgName == orgN

        when: "log in with new user"
        Long staffId = response.json.staff.id
        Long orgId = response.json.staff.org
        response = rest.post("$baseUrl/login") {
            contentType "application/json"
            json {
                username = un
                password = pwd
            }
        }

        then:
        response.status == OK.value()
        response.json.id == staffId
        response.json.name == n
        response.json.email == em
        response.json.status == StaffStatus.ADMIN.toString()
        response.json.roles instanceof List
        response.json.roles.isEmpty() == false
        response.json.roles.contains("ROLE_NO_ROLES") == false
        response.json.roles.contains("ROLE_USER") == true
        response.json.org.id == orgId
        response.json.org.name == orgN
        response.json.org.status == OrgStatus.PENDING.toString()

        when: "view org we just created"
        String newAuthToken = response.json.access_token
        response = rest.get("$baseUrl/v1/organizations/${orgId}") {
            header("Authorization", "Bearer $newAuthToken")
        }

        then:
        response.status == OK.value()
        response.json.organization.id == orgId
        response.json.organization.status == OrgStatus.PENDING.toString()
        response.json.organization.location.address == add
        response.json.organization.location.lat == latitude
        response.json.organization.location.lon == longitude
        response.json.organization.teams == []
        response.json.organization.numAdmins == 1
        response.json.organization.links != null
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
        Map existingOrg = response.json.organizations.find { it.id != null }
        response = rest.post("$baseUrl/v1/public/staff") {
            contentType("application/json")
            json {
                staff {
                    name = n
                    email = em
                    username = un
                    password = pwd
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
        response.json.staff.orgName == existingOrg.name
        response.json.staff.org == existingOrg.id

        when: "log in with new user"
        Long staffId = response.json.staff.id
        Long orgId = response.json.staff.org
        response = rest.post("$baseUrl/login") {
            contentType "application/json"
            json {
                username = un
                password = pwd
            }
        }

        then:
        response.status == OK.value()
        response.json.id == staffId
        response.json.name == n
        response.json.email == em
        response.json.status == StaffStatus.PENDING.toString()
        response.json.roles instanceof List
        response.json.roles.isEmpty() == false
        response.json.roles.contains("ROLE_NO_ROLES") == false
        response.json.roles.contains("ROLE_USER") == true
        response.json.org.id == orgId
        response.json.org.name == existingOrg.name
        response.json.org.status == existingOrg.status

        when: "view org we just created"
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
