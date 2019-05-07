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

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
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

    void "test accurateness of generated basic auth header"() {
        given:
        String email = TestUtils.randEmail()
        String listId = TestUtils.randString()
        String root = TestConstants.TEST_HTTP_ENDPOINT
        String pwd = TestUtils.randString()

        MockedMethod executeRequest = MockedMethod.create(HttpUtils, "executeRequest")
        MockedMethod getApiUri = MockedMethod.create(service, "getApiUri") {
            "${root}/basic-auth/${MailUtils.NO_OP_BASIC_AUTH_USERNAME}/${pwd}"
        }
        service.grailsApplication = GroovyStub(GrailsApplication) {
            getFlatConfig() >> ["textup.apiKeys.mailChimp.apiKey": pwd]
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
            .setUri("${root}/basic-auth/${MailUtils.NO_OP_BASIC_AUTH_USERNAME}/${pwd}")
            .setHeader(header)
            .build()

        Result res = HttpUtils.executeRequest(request) { HttpResponse resp ->
            Result.createSuccess(ResultStatus.convert(resp.statusLine.statusCode))
        }

        then: "Basic auth should match API key"
        res.payload.isSuccess

        cleanup:
        getApiUri?.restore()
    }

    void "test correct list id and email used"() {
        given:
        String email = TestUtils.randEmail()
        String generalUpdatesId = TestUtils.randString()
        String usersId = TestUtils.randString()

        MockedMethod addEmailToList = MockedMethod.create(service, "addEmailToList")
        service.grailsApplication = GroovyStub(GrailsApplication) {
            getFlatConfig() >> [
                "textup.apiKeys.mailChimp.listIds.generalUpdates": generalUpdatesId,
                "textup.apiKeys.mailChimp.listIds.users": usersId
            ]
        }

        when: "adding to general updates list"
        service.addEmailToGeneralUpdatesList(email)

        then: "general updates list ID from environment should be used"
        addEmailToList.latestArgs == [email, generalUpdatesId]

        when: "adding to users list"
        service.addEmailToUsersList(email)

        then: "users list ID from environment should be used"
        addEmailToList.latestArgs == [email, usersId]

        cleanup:
        addEmailToList?.restore()
    }
}
