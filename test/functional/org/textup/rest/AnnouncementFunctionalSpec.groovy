package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.type.CallResponse
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.util.*
import org.textup.validator.*
import static org.springframework.http.HttpStatus.*

class AnnouncementFunctionalSpec extends RestSpec {

    def setup() {
        setupData()
        remote.exec({
            app.config.callParamsList = []
            app.config.textRecipientList = []

            String apiId = "iamsosospecial!"
            ctx.textService.metaClass.send = { BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
                String message, List<MediaElement> media = [] ->
                assert toNums.isEmpty() == false
                TempRecordReceipt temp = new TempRecordReceipt(apiId:apiId)
                temp.contactNumber = toNums[0]
                assert temp.validate()
                // store recipient in list for later retrieval
                app.config.textRecipientList << toNums[0]
                // return temp
                ctx.resultFactory.success(temp)
            }
            ctx.callService.metaClass.start = { PhoneNumber fromNum, PhoneNumber toNum,
                Map afterPickup ->
                TempRecordReceipt temp = new TempRecordReceipt(apiId:apiId)
                temp.contactNumber = toNum
                assert temp.validate()
                // store params in config for later retrieval
                app.config.callParamsList << afterPickup
                // return temp
                ctx.resultFactory.success(temp)
            }
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                TypeConvertingMap params ->
                ctx.resultFactory.success()
            }
            ctx.phoneService.metaClass.moveVoicemail = { String sid ->
                ctx.resultFactory.success()
            }
            ctx.phoneService.metaClass.storeVoicemail = { String sid, int dur ->
                ctx.resultFactory.success().toGroup()
            }
            ctx.storageService.metaClass.uploadAsync = { Collection<UploadItem> uItems ->
                new ResultGroup()
            }
            return
        })
    }

    def cleanup() {
    	cleanupData()
    }

    void "test starting announcement with text and call subscribers"() {
        given:
        String authToken = getAuthToken()
        String sid = "iAmAValidSid"

        // Setting up
        // ----------

        when: "create some incoming sessions subscribed to both call and text"
        String num = "1112223333"
        PhoneNumber pNum = new PhoneNumber(number:num)
        RestResponse response = rest.post("${baseUrl}/v1/sessions") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                session {
                    number = num
                    isSubscribedToCall = true
                    isSubscribedToText = true
                }
            }
        }

        then:
        response.status == CREATED.value()
        response.json.session.number == pNum.e164PhoneNumber
        response.json.session.isSubscribedToText == true
        response.json.session.isSubscribedToCall == true

        when: "list call subscribers"
        response = rest.get("${baseUrl}/v1/sessions?subscribed=call") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then: "make sure no sessions"
        response.status == OK.value()
        response.json.sessions instanceof List

        when: "list text subscribers"
        int numCallSubs = response.json.meta.total
        // list text subs
        response = rest.get("${baseUrl}/v1/sessions?subscribed=text") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then: "make sure no sessions"
        response.status == OK.value()
        response.json.sessions instanceof List

        // Starting announcement
        // ---------------------

        when: "create announcement"
        int numTextSubs = response.json.meta.total
        String msg = "hello!"
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
        Map doneData = remote.exec({
            [
                callParams:app.config.callParamsList,
                textRecipients:app.config.textRecipientList
            ]
        })

        then:
        response.status == CREATED.value()
        response.json.announcement.message == msg
        response.json.announcement.expiresAt == expires.withZone(DateTimeZone.UTC).toString()
        response.json.announcement.isExpired == false
        response.json.announcement.numReceipts == numCallSubs + numTextSubs
        response.json.announcement.numCallReceipts == numCallSubs
        response.json.announcement.numTextReceipts == numTextSubs
        response.json.announcement.numReceipts ==
            doneData.callParams.size() + doneData.textRecipients.size()
        doneData.callParams.every { it.handle == CallResponse.ANNOUNCEMENT_AND_DIGITS }

        // Unsubscribing
        // -------------

        when: "one of the text receipients unsubscribe"
        String toNum = remote.exec({ un ->
            Staff.findByUsername(un).phone.number.e164PhoneNumber
        }.curry(loggedInUsername))
        MultiValueMap<String,String> textForm = new LinkedMultiValueMap<>()
        textForm.add("MessageSid", sid)
        textForm.add("From", num)
        textForm.add("To", toNum)
        textForm.add("NumSegments", "8")
        textForm.add("Body", Constants.TEXT_TOGGLE_SUBSCRIBE)
        response = rest.post("${baseUrl}/v1/public/records") {
            contentType("application/x-www-form-urlencoded")
            body(textForm)
        }

        then:
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(Constants.TEXT_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("stop receiving")

        when: "one of the call receipients unsubscribe"
        MultiValueMap<String,String> callForm = new LinkedMultiValueMap<>()
        callForm.add("CallSid", sid)
        callForm.add("From", toNum) // from is TextUp phone number
        callForm.add("To", num) // to is the client (session)
        callForm.add("Digits", Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE)
        StringBuilder urlString = new StringBuilder()
        urlString << "${baseUrl}/v1/public/records?"
        urlString << "handle=${CallResponse.ANNOUNCEMENT_AND_DIGITS}&"
        urlString << "message=hi&identifier=kiki"
        response = rest.post(urlString.toString()) {
            contentType("application/x-www-form-urlencoded")
            body(callForm)
        }

        then:
        response.status == OK.value()
        response.xml.Say.size() == 2
        response.xml.Hangup.size() == 1
        response.xml.Say[0].toString().contains("stop receiving")

        // Confirming unsubscribe
        // ----------------------

        when: "list subscribers for text"
        response = rest.get("${baseUrl}/v1/sessions?subscribed=text") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == OK.value()
        response.json.sessions instanceof List
        response.json.meta.total == numTextSubs - 1

        when: "list subscribers for call"
        response = rest.get("${baseUrl}/v1/sessions?subscribed=call") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == OK.value()
        response.json.sessions instanceof List
        response.json.meta.total == numCallSubs - 1
    }
}
