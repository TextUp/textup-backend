package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.TestFor
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

@TestFor(OrganizationController)
class OrganizationControllerSpec extends RestSpec {

	String requestUrl = "$baseUrl/v1/organizations"

    def setup() {
    	super.setupData()
    }

    def cleanup() {
    	super.cleanupData()
    }

    void "test list"() {
        given:
        String authToken = getAuthToken()

        when: "we try to list orgs without logging in"
        RestResponse response = rest.get(requestUrl)

        then: "not authorized"
        response.status == UNAUTHORIZED.value()
        response.json == null

        when: "we list orgs without any options after logging in"
        response = rest.get(requestUrl) {
            header("Authorization", "Bearer $authToken")
        }

        then: "success"
        response.status == OK.value()
        response.json?.meta?.total == remote.count(Organization)
        response.json.organizations.size() == (response.json.meta.max > response.json.meta.total) ? response.json.meta.total : response.json.meta.max
    }

    void "test show"() {
        given:
        String authToken = getAuthToken()

        when: "we want to see an org WITHOUT logging in"
        RestResponse response = rest.get("$requestUrl/${remote.orgId}")

        then: "unauthorized"
        response.status == UNAUTHORIZED.value()
        response.json == null

        when: "we want to see an org AFTER logging in"
        response = rest.get("$requestUrl/${remote.orgId}") {
            header("Authorization", "Bearer $authToken")
        }

        then: "ok"
        response.status == OK.value()
        response.json.organization?.id == remote.orgId
    }

    void "test save"() {
        given:
        String authToken = getAuthToken()

        when:
        RestResponse response = rest.post(requestUrl) {
            header("Authorization", "Bearer $authToken")
            contentType("application/json")
            json { 
                organization {
                    name = "A new org"
                    location {
                        address = "A new org's address"
                        lat = 0G
                        lon = 0G
                    }
                }
            }
        }

        then:
        response.status == METHOD_NOT_ALLOWED.value()
    }

    void "test update"() {
        given:
        String authToken = getAuthToken()

        when:
        String newName ="A new org", 
            newAddress = "A new org's address"
        RestResponse response = rest.put("$requestUrl/${remote.orgId}") {
            header("Authorization", "Bearer $authToken")
            contentType("application/json")
            json { 
                organization {
                    name = newName
                    location {
                        address = newAddress
                    }
                }
            }
        }

        then:
        response.status == OK.value()
        response.json.organization?.id == remote.orgId
        response.json.organization.name == newName
        response.json.organization.location.address == newAddress
    }

    void "test delete"() {
    	given:
        String authToken = getAuthToken()

        when: "we try to delete this org"
        RestResponse response = rest.delete("$requestUrl/${remote.orgId}") {
            header("Authorization", "Bearer $authToken")
            contentType("application/json")
        }

        then: "method not allowed"
        response.status == HttpStatus.METHOD_NOT_ALLOWED.value()
        response.json == null
    }
}
