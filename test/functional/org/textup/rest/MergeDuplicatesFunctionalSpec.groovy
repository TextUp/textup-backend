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
class MergeDuplicatesFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        doSetup()
    }

    def cleanup() {
    	doCleanup()
    }

    void "test finding and merging duplicates for an entire phone"() {
        given:
        String authToken = getAuthToken()
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
            IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord(p1)
            3.times {
                IndividualPhoneRecord ipr1Dup = TestUtils.buildIndPhoneRecord(p1, false)
                ipr1Dup.mergeNumber(ipr1.numbers[0], ipr1.numbers[0].preference)
                IndividualPhoneRecord ipr2Dup = TestUtils.buildIndPhoneRecord(p1, false)
                ipr2Dup.mergeNumber(ipr2.numbers[0], ipr2.numbers[0].preference)
            }
            return
        }.curry(loggedInUsername))

        when: "create new staff and organization"
        RestResponse response = rest.get("$baseUrl/v1/duplicates") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }
        int totalNumContacts = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return IndividualPhoneRecord.countByPhoneAndIsDeleted(p1, false)
        }.curry(loggedInUsername))

        then:
        response.status == ResultStatus.OK.intStatus
        response.json."merge-groups".size() == 2 // two merge groups
        response.json."merge-groups".every { it.merges != null }

        when: "use the returned merge groups to merge duplicates"
        Map mergeGroupObj = response.json."merge-groups"[0]
        int numMergedIn = mergeGroupObj.merges.mergeWith[0].size()
        List mergedInIds = mergeGroupObj.merges.mergeWith[0].collect { it.id }
        Collection mergeActionsCollection = [[action: MergeIndividualAction.DEFAULT, mergeIds: mergedInIds]]
        response = rest.put("$baseUrl/v1/contacts/${mergeGroupObj.id}") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                contact {
                    doMergeActions = mergeActionsCollection
                }
            }
        }
        int afterNumContacts = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return IndividualPhoneRecord.countByPhoneAndIsDeleted(p1, false)
        }.curry(loggedInUsername))

        then: "merge success and merged-in do not show up because they are marked as deleted"
        response.status == ResultStatus.OK.intStatus
        response.json.contact instanceof Map
        response.json.contact.id == mergeGroupObj.id
        afterNumContacts == totalNumContacts - numMergedIn
        mergedInIds.every { Long mergedInId ->
            remote.exec({ Long cId ->
                IndividualPhoneRecord.get(cId).isDeleted == true
            }.curry(mergedInId))
        }
    }
}
