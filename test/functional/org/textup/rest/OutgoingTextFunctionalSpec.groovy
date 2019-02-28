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
class OutgoingTextFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        doSetup()
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            s1.personalNumberAsString = null
            s1.save(flush: true, failOnError: true)
            return
        }.curry(loggedInUsername))
    }

    def cleanup() {
        doCleanup()
    }

    void "test outgoing text even with no personal phone number"() {
        given:
        String authToken = getAuthToken()
        String message = TestUtils.randString()
        HashSet sharedIds = new HashSet()
        HashSet contactIds = new HashSet()

        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            TestUtils.buildIndPhoneRecord(p1)
            TestUtils.buildSharedPhoneRecord(null, p1)
            return
        }.curry(loggedInUsername))

        when: "discover shared contacts"
        RestResponse response = rest.get("${baseUrl}/v1/contacts?shareStatus=sharedWithMe") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.contacts instanceof List
        response.json.contacts.isEmpty() == false
        response.json.contacts.every { it.permission != null }

        when: "discover my contacts"
        // store contact ids that are shared with us
        sharedIds += response.json.contacts*.id
        // discover my contacts now
        response = rest.get("${baseUrl}/v1/contacts") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.contacts instanceof List
        response.json.contacts.isEmpty() == false

        when:
        // store my contacts
        response.json.contacts.each {
            if (!sharedIds.contains(it.id)) { contactIds << it.id }
        }
        // establish record item baseline
        Map beforeData = remote.exec({
            [numItems: RecordItem.count(), numReceipts: RecordItemReceipt.count()]
        })
        // send text
        response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                    type = RecordItemType.TEXT.toString()
                    contents = message
                    ids = contactIds + sharedIds
                }
            }
        }
        Map afterData = remote.exec({
            [numItems: RecordItem.count(), numReceipts: RecordItemReceipt.count()]
        })

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json.records instanceof List
        response.json.records.size() == contactIds.size() + sharedIds.size()
        afterData.numItems == beforeData.numItems + contactIds.size() + sharedIds.size()
        afterData.numReceipts == beforeData.numReceipts + contactIds.size() + sharedIds.size()
    }
}
