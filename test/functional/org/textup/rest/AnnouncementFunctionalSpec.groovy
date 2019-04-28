package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.*
import grails.test.mixin.support.*
import javax.servlet.http.HttpServletRequest
import org.joda.time.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.cache.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin) // enables local use of validator classes
class AnnouncementFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        doSetup()
        remote.exec {
            app.config.callParamsList = []
            app.config.textRecipientList = []
            MockedMethod.force(ctx.textService, "send") { fromNum, toNums ->
                assert toNums.isEmpty() == false
                TempRecordReceipt temp = TempRecordReceipt
                    .tryCreate(TestUtils.randString(), toNums[0])
                    .payload
                assert temp.validate()
                // store recipient in list for later retrieval
                app.config.textRecipientList << toNums[0].number
                // return temp
                ctx.resultFactory.success(temp)
            }
            MockedMethod.force(ctx.callService, "start") { fromNum, toNums, pickup ->
                TempRecordReceipt temp = TempRecordReceipt
                    .tryCreate(TestUtils.randString(), toNums[0])
                    .payload
                assert temp.validate()
                // store params in config for later retrieval
                app.config.callParamsList << pickup
                // return temp
                ctx.resultFactory.success(temp)
            }
            return
        }
    }

    def cleanup() {
        doCleanup()
    }

    void "test starting announcement with text and call subscribers"() {
        given:
        String authToken = getAuthToken()
        String sid = TestUtils.randString()

        // Setting up
        // ----------

        when: "create some incoming sessions subscribed to both call and text"
        PhoneNumber pNum = TestUtils.randPhoneNumber()
        RestResponse response = rest.post("${baseUrl}/v1/sessions") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                session {
                    number = pNum.number
                    isSubscribedToCall = true
                    isSubscribedToText = true
                }
            }
        }

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json.session.number == pNum.prettyPhoneNumber
        response.json.session.isSubscribedToText == true
        response.json.session.isSubscribedToCall == true

        when: "list call subscribers"
        Long isId = response.json.session.id
        response = rest.get("${baseUrl}/v1/sessions?subscribedToCall=true&max=100") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.sessions instanceof List
        response.json.sessions.isEmpty() == false
        response.json.sessions.find { it.id == isId }

        when: "list text subscribers"
        int numCallSubs = response.json.meta.total
        // list text subs
        response = rest.get("${baseUrl}/v1/sessions?subscribedToText=true&max=100") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.sessions instanceof List
        response.json.sessions.isEmpty() == false
        response.json.sessions.find { it.id == isId }

        // Starting announcement
        // ---------------------

        when: "create announcement"
        int numTextSubs = response.json.meta.total
        String msg = TestUtils.randString()
        DateTime expires = DateTime.now().plusDays(2)

        response = rest.post("${baseUrl}/v1/announcements") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                announcement {
                    message = msg
                    expiresAt = expires.toString()
                }
            }
        }
        Map doneData = remote.exec {
            [callParams: app.config.callParamsList, textRecipients: app.config.textRecipientList]
        }

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json.announcement.message == msg
        response.json.announcement.expiresAt == expires.withZone(DateTimeZone.UTC).toString()
        response.json.announcement.isExpired == false
        response.json.announcement.receipts.recipients.size() == numCallSubs + numTextSubs
        response.json.announcement.receipts.recipients.size() ==
            doneData.callParams.size() + doneData.textRecipients.size()
        response.json.announcement.receipts.callRecipients.size() == numCallSubs
        response.json.announcement.receipts.textRecipients.size() == numTextSubs
        doneData.callParams.every { it.handle == CallResponse.ANNOUNCEMENT_AND_DIGITS }

        // Unsubscribing
        // -------------

        when: "one of the text receipients unsubscribe"
        String toNumString = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            IOCUtils.phoneCache
                .findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
                .number
                .e164PhoneNumber
        }.curry(loggedInUsername))
        MultiValueMap textForm = new LinkedMultiValueMap()
        textForm.add("MessageSid", sid)
        textForm.add("From", pNum.number)
        textForm.add("To", toNumString)
        textForm.add("NumSegments", "8")
        textForm.add("Body", TextTwiml.BODY_TOGGLE_SUBSCRIBE)
        response = rest.post("${baseUrl}/v1/public/records") {
            contentType("application/x-www-form-urlencoded")
            body(textForm)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(TextTwiml.BODY_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("stop receiving")

        when: "one of the call receipients unsubscribe"
        MultiValueMap callForm = new LinkedMultiValueMap()
        callForm.add("CallSid", sid)
        callForm.add("From", toNumString) // from is TextUp phone number
        callForm.add("To", pNum.number) // to is the client (session)
        callForm.add("Digits", CallTwiml.DIGITS_ANNOUNCEMENT_UNSUBSCRIBE)
        StringBuilder urlString = new StringBuilder()
        urlString << "${baseUrl}/v1/public/records?"
        urlString << "${CallbackUtils.PARAM_HANDLE}=${CallResponse.ANNOUNCEMENT_AND_DIGITS}&"
        urlString << "${CallTwiml.ANNOUNCEMENT_AND_DIGITS_MSG}=hi&"
        urlString << "${CallTwiml.ANNOUNCEMENT_AND_DIGITS_IDENT}=kiki&"
        response = rest.post(urlString.toString()) {
            contentType("application/x-www-form-urlencoded")
            body(callForm)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml.Say.size() == 2
        response.xml.Hangup.size() == 1
        response.xml.Say[0].toString().contains("stop receiving")

        // Confirming unsubscribe
        // ----------------------

        when: "list subscribers for text"
        response = rest.get("${baseUrl}/v1/sessions?subscribedToText=true") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.sessions instanceof List
        response.json.meta.total == numTextSubs - 1

        when: "list subscribers for call"
        response = rest.get("${baseUrl}/v1/sessions?subscribedToCall=true") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.sessions instanceof List
        response.json.meta.total == numCallSubs - 1
    }
}
