package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

class IncomingTextFunctionalSpec extends RestSpec {

    String requestUrl = "${baseUrl}/v1/public/records"

    def setup() {
        setupData()
        remote.exec({
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                GrailsParameterMap params ->
                ctx.resultFactory.success()
            }
            String apiId = "iamsosospecial!"
            ctx.textService.metaClass.send = { BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
                String message, List<MediaElement> media = [] ->

                assert toNums.isEmpty() == false
                TempRecordReceipt temp = new TempRecordReceipt(apiId:apiId)
                temp.contactNumber = toNums[0]
                assert temp.validate()
                ctx.resultFactory.success(temp)
            }
            ctx.mediaService.metaClass.buildFromIncomingMedia = { Map<String, String> urlToMimeType,
                Closure<Void> collectUploads, Closure<Void> collectMediaIds ->

                MediaInfo mInfo = new MediaInfo()
                mInfo.save(failOnError: true)
                ctx.resultFactory.success(mInfo)
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

    void "test incoming text without announcements"() {
        given: "phone without announcements, none available"
        String sid = "iAmAValidSid"
        String fromNum = "+13829182928"
        String toNum = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
            //ensure that incoming session doesn't exist
            PhoneNumber fromPNum = new PhoneNumber(number:fNum)
            assert IncomingSession.countByPhoneAndNumberAsString(s1.phone, fromPNum.number) == 0
            return s1.phone.number.e164PhoneNumber
        }.curry(loggedInUsername, fromNum))

        when: "none available"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("MessageSid", sid)
        form.add("From", fromNum)
        form.add("To", toNum)
        form.add("Body", "hi!")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "away message and marked as away, no instructions"
        response.status == OK.value()
        response.xml.Message.size() == 1

        when: "some available"
        remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
            return
        }.curry(loggedInUsername, fromNum))
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "no response, no instructions"
        response.status == OK.value()
        response.xml.Message.size() == 0
        response.xml.text() == ""
    }

    void "test incoming text with announcements"() {
        given: "session not subscribed, none available, should send instructions"
        String sid = "iAmAValidSid"
        String fromNum = "+12348398298"
        Map data = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
            Phone p1 = s1.phone
            // add announcement
            FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
                message:"hello!", expiresAt:DateTime.now().plusDays(2))
            announce.save(flush:true, failOnError:true)
            //ensure that incoming session doesn't exist
            PhoneNumber fromPNum = new PhoneNumber(number:fNum)
            assert IncomingSession.countByPhoneAndNumberAsString(p1, fromPNum.number) == 0
            return [
                to:p1.number.e164PhoneNumber,
                announce:announce.message,
                away:p1.awayMessage
            ]
        }.curry(loggedInUsername, fromNum))
        String toNum = data.to,
            announce = data.announce,
            away = data.away

        when: "incoming text"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("MessageSid", sid)
        form.add("From", fromNum)
        form.add("To", toNum)
        form.add("Body", "hi!")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "away message and marked as away, no instructions"
        response.status == OK.value()
        response.xml.Message.size() == 2
        response.xml.Message.any { it.toString() == away }
        response.xml.Message.any {
            it.toString().contains(Constants.TEXT_TOGGLE_SUBSCRIBE) &&
                it.toString().contains(Constants.TEXT_SEE_ANNOUNCEMENTS)
        }

        when: "incoming text again"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "instructions only shown once per day"
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString() == away

        when: "see announcements"
        form.set("Body", Constants.TEXT_SEE_ANNOUNCEMENTS)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(announce)

        when: "toggle subscription when unsubscribed"
        form.set("Body", Constants.TEXT_TOGGLE_SUBSCRIBE)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(Constants.TEXT_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("receive")

        when: "toggle subscription when subscribed"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(Constants.TEXT_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("stop receiving")
    }

    void "test incoming media via text message"() {
        given: "new phone number + media item + staff is available"
        String encodedData = TestHelpers.encodeBase64String(TestHelpers.getJpegSampleData512())
        String checksum = TestHelpers.getChecksum(encodedData)
        PhoneNumber fromNum = new PhoneNumber(number:TestHelpers.randPhoneNumber())
        String sid = TestHelpers.randString()
        String sampleUrl = "http://www.example.com"
        String toNum = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(failOnError: true)
            s1.phone.number.e164PhoneNumber
        }.curry(loggedInUsername))

        when: "incoming message from a new phone number with media"
        Map beforeCounts = remote.exec({
            [
                contacts: Contact.count(),
                contactNumbers: ContactNumber.count(),
                mediaInfo: MediaInfo.count(),
                sessions: IncomingSession.count(),
            ]
        })
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("MessageSid", sid)
        form.add("From", fromNum.number)
        form.add("To", toNum)
        form.add("Body", "hi!")
        form.add("NumMedia", "2")
        form.add("MediaUrl0", sampleUrl)
        form.add("MediaContentType0", MediaType.IMAGE_JPEG.mimeType)
        form.add("MediaUrl1", sampleUrl)
        form.add("MediaContentType1", MediaType.IMAGE_JPEG.mimeType)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }
        Map afterCounts = remote.exec({
            [
                contacts: Contact.count(),
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
        response.status == OK.value()
        response.xml.Message.size() == 0
        response.xml.text() == ""
    }
}
