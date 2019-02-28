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
class OutgoingCallFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        doSetup()
    }

    def cleanup() {
        doCleanup()
    }

    void "test outgoing bridge call"() {
        given:
        String authToken = getAuthToken()
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            TestUtils.buildIndPhoneRecord(p1)
            return
        }.curry(loggedInUsername))

        when: "discover contacts"
        RestResponse response = rest.get("${baseUrl}/v1/contacts") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.json.contacts instanceof List
        response.json.contacts.isEmpty() == false
        response.json.contacts.any { it.permission == null } // owned contacts

        when: "start bridge"
        // store my contacts
        int iprId = response.json.contacts.find { it.permission == null }.id // owned contacts
        // establish record item baseline
        Map beforeData = remote.exec({
            [numItems: RecordItem.count(), numReceipts: RecordItemReceipt.count()]
        })
        // send call
        response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                    type = RecordItemType.CALL.toString()
                    ids = [iprId]
                }
            }
        }
        Map afterData = remote.exec({
            [numItems: RecordItem.count(), numReceipts: RecordItemReceipt.count()]
        })

        then:
        response.status == ResultStatus.CREATED.intStatus
        response.json.records instanceof List
        response.json.records.size() == 1
        afterData.numItems == beforeData.numItems + 1
        afterData.numReceipts == beforeData.numReceipts + 1

        when: "finish bridge"
        String cId = iprId
        String hand = CallResponse.FINISH_BRIDGE.toString()
        // getting from and to numbers
        Map numData = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return [
                from: p1.number.e164PhoneNumber,
                to: s1.personalNumber.e164PhoneNumber
            ]
        }.curry(loggedInUsername))
        // make finish bridge request
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add(TwilioUtils.ID_CALL, TestUtils.randString())
        form.add(TwilioUtils.FROM, numData.from)
        form.add(TwilioUtils.TO, numData.to)
        response = rest.post("${baseUrl}/v1/public/records?${CallTwiml.FINISH_BRIDGE_WRAPPER_ID}=${cId}&${CallbackUtils.PARAM_HANDLE}=${hand}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml.Dial.size() > 0
        response.xml.Dial.Number.every {
            it.@statusCallback.toString().contains(CallbackUtils.PARAM_HANDLE) &&
                it.@statusCallback.toString().contains(CallbackUtils.PARAM_CHILD_CALL_NUMBER)
        }
    }
}
