package org.textup.util

import grails.test.mixin.*
import grails.test.mixin.web.ControllerUnitTestMixin
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.*
import org.apache.http.Header
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import spock.lang.Specification

@TestMixin(ControllerUnitTestMixin)
@TestFor(MarketingMailService)
class MarketingMailServiceSpec extends Specification {

    String generalUpdatesId
    String pwd
    String usersId

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()

        pwd = TestUtils.randString()
        generalUpdatesId = TestUtils.randString()
        usersId = TestUtils.randString()

        service.grailsApplication = GroovyStub(GrailsApplication) {
            getFlatConfig() >> [
                "textup.apiKeys.mailChimp.apiKey": pwd,
                "textup.apiKeys.mailChimp.listIds.generalUpdates": generalUpdatesId,
                "textup.apiKeys.mailChimp.listIds.users": usersId
            ]
        }
    }

    void "test request object is built correctly"() {
        given:
        String email = TestUtils.randEmail()
        String listId = TestUtils.randString()
        
        MockedMethod executeRequest = MockedMethod.create(HttpUtils, "executeRequest")

        when: "adding email to list"
        service.addEmailToList(email, listId)
        HttpUriRequest request = executeRequest.latestArgs[0]
        String body = IOUtils.toString(request.entity.content)

        then: "request should be a post method with specific properties"
        executeRequest.callCount == 1
        request.method == "POST"
        request.URI == new URI("https://us11.api.mailchimp.com/3.0/lists/${listId}/members")
        body.contains(email)
        body.contains("subscribed")

        cleanup:
        executeRequest?.restore()
    }

    void "test building accurateness of generated basic auth header"() {
        given:
        String email = TestUtils.randEmail()
        String listId = TestUtils.randString()
        String root = TestConstants.TEST_HTTP_ENDPOINT
        String user = "user"
        Integer statusCode

        MockedMethod executeRequest = MockedMethod.create(HttpUtils, "executeRequest")

        MockedMethod getApiUri = MockedMethod.create(service, "getApiUri") {
            "${root}/basic-auth/user/${pwd}"
        }

        when: "adding email to list"
        service.addEmailToList(email, listId)
        def header = executeRequest.latestArgs[0].allHeaders[0]

        then: "authorization should be basic"
        header.getName() == "Authorization"
        header.getValue().contains("Basic")

        when: "request is made with API key"
        executeRequest?.restore()
        HttpUriRequest request = RequestBuilder
            .get()
            .setUri("${root}/basic-auth/user/${pwd}")
            .setHeader(header)
            .build()

        Result<?> res = HttpUtils.executeRequest(request) { HttpResponse resp ->
            statusCode = resp.statusLine.statusCode
            Result.void()
        }

        then: "Basic auth should match API key"
        res.status == ResultStatus.NO_CONTENT
        statusCode < 400

        cleanup:
        getApiUri?.restore()
    }

    void "test correct list id and email used"() {
        given:
        String email = TestUtils.randEmail()
        String emailUsed
        String listIdUsed

        MockedMethod addEmailToList = MockedMethod.create(service, "addEmailToList")

        when: "adding to general updates list"
        service.addEmailToGeneralUpdatesList(email)
        listIdUsed = addEmailToList.latestArgs[1]
        emailUsed = addEmailToList.latestArgs[0]

        then: "general updates list ID from environment should be used"
        listIdUsed == generalUpdatesId
        emailUsed == email

        when: "adding to users list"
        service.addEmailToUsersList(email)
        listIdUsed = addEmailToList.latestArgs[1]
        emailUsed = addEmailToList.latestArgs[0]

        then: "users list ID from environment should be used"
        listIdUsed == usersId
        emailUsed == email

        cleanup:
        addEmailToList?.restore()
    }
}
