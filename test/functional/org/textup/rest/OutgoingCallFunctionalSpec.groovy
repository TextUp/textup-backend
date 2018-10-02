package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.joda.time.DateTime
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.type.CallResponse
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.util.*
import org.textup.validator.BasePhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

class OutgoingCallFunctionalSpec extends RestSpec {

    String _apiId

    def setup() {
        setupData()
        _apiId = remote.exec({
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                TypeConvertingMap params ->
                ctx.resultFactory.success()
            }
            String apiId = "iamsosospecial!"
            ctx.callService.metaClass.start = { BasePhoneNumber fromNum, BasePhoneNumber toNum,
                Map afterPickup ->

                TempRecordReceipt temp = new TempRecordReceipt(apiId:apiId)
                temp.contactNumber = toNum
                assert temp.validate()
                // return temp
                ctx.resultFactory.success(temp)
            }
            return apiId
        })
    }

    def cleanup() {
        cleanupData()
    }

    void "test outgoing bridge call"() {
        given:
        String authToken = getAuthToken()

        when: "discover shared contacts"
        RestResponse response = rest.get("${baseUrl}/v1/contacts") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == OK.value()
        response.json.contacts instanceof List
        response.json.contacts.isEmpty() == false
        response.json.contacts.any { it.permission == null }

        when: "start bridge"
        // store my contacts
        int contactId = response.json.contacts.find { it.permission == null }.id
        // establish record item baseline
        Map beforeData = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count()
            ]
        })
        // send call
        response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                    callContact = contactId
                }
            }
        }
        Map afterData = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count()
            ]
        })

        then:
        response.status == CREATED.value()
        response.json.records instanceof List
        response.json.records.size() == 1
        afterData.numItems == beforeData.numItems + 1
        afterData.numReceipts == beforeData.numReceipts + 1

        when: "finish bridge"
        String cId = contactId,
            hand = CallResponse.FINISH_BRIDGE.toString()
        // getting from and to numbers
        Map numData = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            return [
                from:s1.phone.number.e164PhoneNumber,
                to:s1.personalPhoneNumber.e164PhoneNumber
            ]
        }.curry(loggedInUsername))
        // make finish bridge request
        String sid = "iAmAValidSid"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", sid)
        form.add("From", numData.from)
        form.add("To", numData.to)
        form.add("Direction", "outbound-api")
        response = rest.post("${baseUrl}/v1/public/records?contactId=${cId}&handle=${hand}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml.Dial.size() > 0
        response.xml.Dial.Number.every {
            it.@statusCallback.toString().contains(Constants.CALLBACK_STATUS) &&
                it.@statusCallback.toString().contains(Constants.CALLBACK_CHILD_CALL_NUMBER_KEY)
        }
    }
}
