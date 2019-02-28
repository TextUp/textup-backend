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
import org.textup.validator.action.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin) // enables local use of validator classes
class OutgoingMediaFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        doSetup()
    }

    def cleanup() {
        doCleanup()
    }

    void "test scheduling a future message call with media"() {
        given:
        String authToken = getAuthToken()
        String thisMessage = TestUtils.randString()
        String thisData = TestUtils.encodeBase64String(TestUtils.getJpegSampleData256())
        String thisChecksum = TestUtils.getChecksum(thisData)
        Long iprId = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return TestUtils.buildIndPhoneRecord(p1).id
        }.curry(loggedInUsername))
        List mediaActions = []
        2.times {
            mediaActions << [
                action: MediaAction.ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: thisData,
                checksum: thisChecksum
            ]
        }

        when:
        Map beforeCounts = remote.exec({
            [
                numFuture: FutureMessage.count(),
                numItems: RecordItem.count(),
                numReceipts: RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })
        RestResponse response = rest.post("${baseUrl}/v1/future-messages?contactId=${iprId}") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                "future-message" {
                    message = thisMessage
                    type = FutureMessageType.CALL.toString().toLowerCase()
                    startDate = DateTime.now().minusDays(2).toString()
                    doMediaActions = mediaActions
                }
            }
        }
        Map afterCounts = remote.exec({
            [
                numFuture: FutureMessage.count(),
                numItems: RecordItem.count(),
                numReceipts: RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })

        then: "future message created but not sent out yet!"
        response.status == ResultStatus.CREATED.intStatus
        response.json["future-message"] instanceof Map
        response.json["future-message"].message == thisMessage
        (response.json["future-message"].media.uploadErrors instanceof List) == false
        response.json["future-message"].media.images instanceof List
        response.json["future-message"].media.images.size() == mediaActions.size()
        response.json["future-message"].media.images.each {
            assert it.versions.every {
                it.type == MediaType.IMAGE_JPEG.mimeType && it.link instanceof String
            }
        }
        response.json["future-message"].media.audio.isEmpty()
        afterCounts.numItems == beforeCounts.numItems
        afterCounts.numReceipts == beforeCounts.numReceipts
        afterCounts.numMediaInfo == beforeCounts.numMediaInfo + 1
        afterCounts.numFuture == beforeCounts.numFuture + 1
    }

    void "test sending outgoing media via a text message"() {
        given:
        String authToken = getAuthToken()
        String thisMessage = TestUtils.randString()
        String thisData = TestUtils.encodeBase64String(TestUtils.getJpegSampleData256())
        String thisChecksum = TestUtils.getChecksum(thisData)
        Long iprId = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return TestUtils.buildIndPhoneRecord(p1).id
        }.curry(loggedInUsername))
        List mediaActions = []
        (ValidationUtils.MAX_NUM_MEDIA_PER_MESSAGE * 2).times {
            mediaActions << [
                action: MediaAction.ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: thisData,
                checksum: thisChecksum
            ]
        }

        when:
        Map beforeCounts = remote.exec({
            [
                numItems: RecordItem.count(),
                numReceipts: RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })
        RestResponse response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer ${authToken}")
            json {
                record {
                    type = RecordItemType.TEXT.toString()
                    ids = [iprId]
                    contents = thisMessage
                    doMediaActions = mediaActions
                }
            }
        }
        Map afterCounts = remote.exec({
            [
                numItems: RecordItem.count(),
                numReceipts: RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json.records instanceof List
        response.json.records.size() == 1
        response.json.records[0].contents == thisMessage
        (response.json.records[0].media.uploadErrors instanceof List) == false
        response.json.records[0].media?.images instanceof List
        response.json.records[0].media?.images.size() == mediaActions.size()
        response.json.records[0].media?.images.each {
            assert it.versions.every {
                it.type == MediaType.IMAGE_JPEG.mimeType && it.link instanceof String
            }
        }
        response.json.records[0].media?.audio.isEmpty()
        afterCounts.numItems == beforeCounts.numItems + 1
        afterCounts.numReceipts == beforeCounts.numReceipts +
            Math.ceil(mediaActions.size() / ValidationUtils.MAX_NUM_MEDIA_PER_MESSAGE)
        afterCounts.numMediaInfo == beforeCounts.numMediaInfo + 1
    }

    void "test upload outgoing media with some upload errors"() {
        given: "re-override storage service to have some upload errors"
        String errorMsg = TestUtils.randString()
        remote.exec({ thisError ->
            MockedMethod.force(ctx.storageService, "uploadAsync") {
                Result.createError([thisError], ResultStatus.BAD_REQUEST).toGroup()
            }
            return
        }.curry(errorMsg))

        and: "set up other necessary info to create outgoing message"
        String authToken = getAuthToken()
        String thisMessage = TestUtils.randString()
        String thisData = TestUtils.encodeBase64String(TestUtils.getJpegSampleData256())
        String thisChecksum = TestUtils.getChecksum(thisData)
        Long iprId = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return TestUtils.buildIndPhoneRecord(p1).id
        }.curry(loggedInUsername))
        List mediaActions = []
        2.times {
            mediaActions << [
                action: MediaAction.ADD,
                mimeType: MediaType.IMAGE_JPEG.mimeType,
                data: thisData,
                checksum: thisChecksum
            ]
        }

        when:
        Map beforeCounts = remote.exec({
            [
                numItems: RecordItem.count(),
                numReceipts: RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })
        RestResponse response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                    type = RecordItemType.TEXT.toString()
                    ids = [iprId]
                    contents = thisMessage
                    doMediaActions = mediaActions
                }
            }
        }
        Map afterCounts = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count(),
                numMediaInfo: MediaInfo.count(),
            ]
        })

        then: "media is created and message is sent normally, but upload errors are noted"
        response.status == ResultStatus.CREATED.intStatus
        response.json.records instanceof List
        response.json.records.size() == 1
        response.json.records[0].contents == thisMessage
        response.json.records[0].media.uploadErrors instanceof List
        response.json.records[0].media.uploadErrors.contains(errorMsg)
        response.json.records[0].media.images instanceof List
        response.json.records[0].media.images.size() == mediaActions.size()
        response.json.records[0].media.images.each {
            assert it.versions.every {
                it.type == MediaType.IMAGE_JPEG.mimeType && it.link instanceof String
            }
        }
        response.json.records[0].media.audio.isEmpty()
        afterCounts.numItems == beforeCounts.numItems + 1
        afterCounts.numReceipts == beforeCounts.numReceipts +
            Math.ceil(mediaActions.size() / ValidationUtils.MAX_NUM_MEDIA_PER_MESSAGE)
        afterCounts.numMediaInfo == beforeCounts.numMediaInfo + 1
    }
}
