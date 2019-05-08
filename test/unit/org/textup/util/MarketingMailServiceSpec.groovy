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

    void "test adding to general updates list"() {
        given:
        String email = TestUtils.randEmail()
        String generalUpdatesId = TestUtils.randString()

        service.grailsApplication = GroovyStub(GrailsApplication) {
            getFlatConfig() >> [
                "textup.apiKeys.mailChimp.listIds.generalUpdates": generalUpdatesId,
            ]
        }
        service.threadService = GroovyMock(ThreadService)
        MockedMethod addEmailToList = MockedMethod.create(service, "addEmailToList") { Result.void() }

        when:
        Result res = service.tryScheduleAddToGeneralUpdatesList(false, email)

        then:
        res.status == ResultStatus.NO_CONTENT
        0 * service.threadService._
        addEmailToList.notCalled

        when:
        res = service.tryScheduleAddToGeneralUpdatesList(true, email)

        then:
        res.status == ResultStatus.NO_CONTENT
        1 * service.threadService.submit(*_) >> { args -> args[0].call(); null; }

        and:
        addEmailToList.latestArgs == [email, generalUpdatesId]

        cleanup:
        addEmailToList?.restore()
    }

    void "test adding to user training list"() {
        given:
        String email = TestUtils.randEmail()
        String usersId = TestUtils.randString()

        Staff s1 = GroovyMock() {
            asBoolean() >> true
            getEmail() >> email
        }
        service.grailsApplication = GroovyStub(GrailsApplication) {
            getFlatConfig() >> [
                "textup.apiKeys.mailChimp.listIds.users": usersId
            ]
        }
        service.threadService = GroovyMock(ThreadService)
        MockedMethod addEmailToList = MockedMethod.create(service, "addEmailToList") { Result.void() }

        when: "no staff passed in"
        Result res = service.tryScheduleAddToUserTrainingList(null)

        then:
        res.status == ResultStatus.NO_CONTENT
        0 * service.threadService._
        addEmailToList.notCalled

        when: "no prior status + staff is currently pending"
        res = service.tryScheduleAddToUserTrainingList(s1)

        then:
        res.status == ResultStatus.NO_CONTENT
        s1.status >> StaffStatus.PENDING
        0 * service.threadService._

        when: "active prior status + staff is active"
        res = service.tryScheduleAddToUserTrainingList(s1, StaffStatus.STAFF)

        then:
        res.status == ResultStatus.NO_CONTENT
        s1.status >> StaffStatus.STAFF
        0 * service.threadService._

        when: "pending prior status + staff is NOT active"
        res = service.tryScheduleAddToUserTrainingList(s1, StaffStatus.PENDING)

        then:
        res.status == ResultStatus.NO_CONTENT
        s1.status >> StaffStatus.PENDING
        0 * service.threadService._

        when: "pending prior status + staff IS active"
        res = service.tryScheduleAddToUserTrainingList(s1, StaffStatus.PENDING)

        then: "will try to add to user training list"
        res.status == ResultStatus.NO_CONTENT
        s1.status >> StaffStatus.STAFF
        1 * service.threadService.submit(*_) >> { args -> args[0].call(); null; }

        and:
        addEmailToList.callCount == 1
        addEmailToList.latestArgs == [email, usersId]

        when: "no old status + staff is active"
        res = service.tryScheduleAddToUserTrainingList(s1)

        then: "will try to add to user training list"
        res.status == ResultStatus.NO_CONTENT
        s1.status >> StaffStatus.STAFF
        1 * service.threadService.submit(*_) >> { args -> args[0].call(); null; }

        and:
        addEmailToList.callCount == 2
        addEmailToList.latestArgs == [email, usersId]

        cleanup:
        addEmailToList?.restore()
    }
}
