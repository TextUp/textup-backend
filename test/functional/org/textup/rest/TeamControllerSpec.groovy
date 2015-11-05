package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.TestFor
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

@TestFor(TeamController)
class TeamControllerSpec extends RestSpec {

	String requestUrl = "$baseUrl/v1/teams"

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
        RestResponse response = rest.get("$requestUrl?organizationId=${remote.orgId}")

        then: "not authorized"
        response.status == UNAUTHORIZED.value()
        response.json == null

        when: "we list orgs without any options after logging in"
        response = rest.get("$requestUrl?organizationId=${remote.orgId}") {
            header("Authorization", "Bearer $authToken")
        }

        then: "success"
        response.status == OK.value()
        response.json?.meta?.total < remote.count(Team)
        response.json.organizations.size() == (response.json.meta.max > response.json.meta.total) ? response.json.meta.total : response.json.meta.max
    }

    void "test show"() {
        given:
        String authToken = getAuthToken()

        when: "we want to see an org WITHOUT logging in"
        RestResponse response = rest.get("$requestUrl/${remote.teamId}")

        then: "unauthorized"
        response.status == UNAUTHORIZED.value()
        response.json == null

        when: "we want to see an org AFTER logging in"
        response = rest.get("$requestUrl/${remote.teamId}") {
            header("Authorization", "Bearer $authToken")
        }

        then: "ok"
        response.status == OK.value()
        response.json.organization?.id == remote.teamId
    }

    void "test save"() {

    }

    void "test update"() {

    }

    void "test delete"() {
    	
    }
}
