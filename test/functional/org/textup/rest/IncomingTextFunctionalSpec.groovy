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
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin) // enables local use of validator classes
class IncomingTextFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String requestUrl = "${baseUrl}/v1/public/records"

    def setup() {
        doSetup()
    }

    def cleanup() {
        doCleanup()
    }

    void "test incoming text without announcements"() {
        given: "phone without announcements, none available"
        String sid = TestUtils.randString()
        String fromNum = TestUtils.randPhoneNumberString()
        String toNum = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingTextFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = false
                }
            return p1.number.e164PhoneNumber
        }.curry(loggedInUsername))

        when: "none available"
        MultiValueMap form = new LinkedMultiValueMap()
        form.add(TwilioUtils.ID_TEXT, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        form.add(TwilioUtils.BODY, "hi!")
        form.add(TwilioUtils.NUM_SEGMENTS, "3")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "away message and marked as away, no instructions"
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 1

        when: "some available"
        remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingTextFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = true
                }
            return
        }.curry(loggedInUsername, fromNum))
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "no response, no instructions"
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 0
        response.xml.text() == ""
    }

    void "test incoming text with announcements"() {
        given: "session not subscribed, none available, should send instructions"
        String sid = TestUtils.randString()
        String fromNum = TestUtils.randPhoneNumberString()
        def (String toNum, String faMsg, String awayMsg) = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingTextFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = false
                }
            FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)
            return [p1.number.e164PhoneNumber, fa1.message, p1.awayMessage]
        }.curry(loggedInUsername))

        when: "incoming text"
        MultiValueMap form = new LinkedMultiValueMap()
        form.add(TwilioUtils.ID_TEXT, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        form.add(TwilioUtils.BODY, "hi!")
        form.add(TwilioUtils.NUM_SEGMENTS, "8")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "away message and marked as away, no instructions"
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 2
        // away message is returned but also augmented with the away message suffix
        response.xml.Message.any { it.toString().contains(awayMsg) && it.toString() != awayMsg }
        response.xml.Message.any {
            it.toString().contains(TextTwiml.BODY_TOGGLE_SUBSCRIBE) &&
                it.toString().contains(TextTwiml.BODY_SEE_ANNOUNCEMENTS)
        }

        when: "incoming text again"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "instructions only shown once per day"
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(awayMsg)

        when: "see announcements"
        form.set(TwilioUtils.BODY, TextTwiml.BODY_SEE_ANNOUNCEMENTS)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(faMsg)

        when: "toggle subscription when unsubscribed"
        form.set(TwilioUtils.BODY, TextTwiml.BODY_TOGGLE_SUBSCRIBE)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(TextTwiml.BODY_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("receive")

        when: "toggle subscription when subscribed"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(TextTwiml.BODY_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("stop receiving")
    }

    void "test incoming media via text message"() {
        given: "new phone number + media item + staff is available"
        String encodedData = TestUtils.encodeBase64String(TestUtils.getJpegSampleData512())
        String checksum = TestUtils.getChecksum(encodedData)
        String fromNum = TestUtils.randPhoneNumberString()
        String sampleUrl = "${TestConstants.TEST_HTTP_ENDPOINT}/image/jpeg"
        String toNum = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingTextFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = true
                }
            return p1.number.e164PhoneNumber
        }.curry(loggedInUsername))

        when: "incoming message from a new phone number with media"
        Map beforeCounts = remote.exec({
            [
                contacts: IndividualPhoneRecord.count(),
                contactNumbers: ContactNumber.count(),
                mediaInfo: MediaInfo.count(),
                sessions: IncomingSession.count(),
            ]
        })
        MultiValueMap form = new LinkedMultiValueMap()
        form.add(TwilioUtils.ID_TEXT, TestUtils.randString())
        form.add(TwilioUtils.ID_ACCOUNT, TestUtils.randString())
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        form.add(TwilioUtils.BODY, "hi!")
        form.add(TwilioUtils.NUM_MEDIA, "2")
        form.add(TwilioUtils.NUM_SEGMENTS, "8")
        form.add("${TwilioUtils.MEDIA_URL_PREFIX}0".toString(), sampleUrl)
        form.add("${TwilioUtils.MEDIA_CONTENT_TYPE_PREFIX}0".toString(), MediaType.IMAGE_JPEG.mimeType)
        form.add("${TwilioUtils.MEDIA_URL_PREFIX}1".toString(), sampleUrl)
        form.add("${TwilioUtils.MEDIA_CONTENT_TYPE_PREFIX}1".toString(), MediaType.IMAGE_JPEG.mimeType)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        Map afterCounts = remote.exec({
            [
                contacts: IndividualPhoneRecord.count(),
                contactNumbers: ContactNumber.count(),
                mediaInfo: MediaInfo.count(),
                sessions: IncomingSession.count(),
            ]
        })

        then: "session created, contact w/ number and media info created"
        afterCounts.contacts == beforeCounts.contacts + 1
        afterCounts.contactNumbers == beforeCounts.contactNumbers + 1
        afterCounts.mediaInfo == beforeCounts.mediaInfo + 1
        afterCounts.sessions == beforeCounts.sessions + 1
        response.status == ResultStatus.OK.intStatus
        response.xml.Message.size() == 0
        response.xml.text() == ""
    }
}
