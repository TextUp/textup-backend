package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.types.OrgStatus
import org.textup.types.StaffStatus
import org.textup.validator.PhoneNumber
import org.textup.validator.BasePhoneNumber
import org.textup.validator.TempRecordReceipt
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

class OutgoingTextFunctionalSpec extends RestSpec {

    String _apiId

    def setup() {
        setupData()
        _apiId = remote.exec {
            String apiId = "iamsosospecial!"
            ctx.textService.metaClass.send = { BasePhoneNumber fromNum,
                List<? extends BasePhoneNumber> toNums, String message ->
                assert toNums.isEmpty() == false
                TempRecordReceipt temp = new TempRecordReceipt(apiId:apiId)
                temp.receivedBy = toNums[0]
                assert temp.validate()
                ctx.resultFactory.success(temp)
            }
            return apiId
        }
    }

    def cleanup() {
        cleanupData()
    }

    void "test outgoing text"() {
        given:
        String authToken = getAuthToken()
        String message = "hey you!"
        HashSet<Long> sharedIds = new HashSet<>(),
            contactIds = new HashSet<>()

        when: "discover shared contacts"
        RestResponse response = rest.get("${baseUrl}/v1/contacts?shareStatus=sharedWithMe") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }

        then:
        response.status == OK.value()
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
        response.status == OK.value()
        response.json.contacts instanceof List
        response.json.contacts.isEmpty() == false

        when:
        // store my contacts
        response.json.contacts.each {
            if (!sharedIds.contains(it.id)) {
                contactIds << it.id
            }
        }
        // establish record item baseline
        Map beforeData = remote.exec({
            [
                numItems:RecordItem.count(),
                numReceipts:RecordItemReceipt.count()
            ]
        })
        // send text
        response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                    contents = message
                    sendToContacts = contactIds
                    sendToSharedContacts = sharedIds
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
        response.json.records.size() == contactIds.size() + sharedIds.size()
        afterData.numItems == beforeData.numItems + contactIds.size() + sharedIds.size()
        afterData.numReceipts == beforeData.numReceipts + contactIds.size() + sharedIds.size()
    }
}