package org.textup.rest

import grails.plugins.rest.client.RestResponse
import org.textup.*
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

class MergeDuplicatesFunctionalSpec extends RestSpec {

    def setup() {
        setupData()
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Contact c1 = s1.phone.createContact([:], ["1112223333"]).payload,
                c2 = s1.phone.createContact([:], ["1231231234"]).payload
            [c1, c2]*.save(flush:true, failOnError:true)
            3.times {
                s1.phone.createContact([:], c1.numbers)
                s1.phone.createContact([:], c2.numbers)
            }
        }.curry(loggedInUsername))
    }

    def cleanup() {
    	cleanupData()
    }

    void "test finding and merging duplicates for an entire phone"() {
        given:
        String authToken = getAuthToken()

        when: "create new staff and organization"
        RestResponse response = rest.get("$baseUrl/v1/contacts?duplicates=true") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
        }
        int totalNumContacts = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            return s1.phone.contacts.size()
        }.curry(loggedInUsername))

        then:
        response.status == OK.value()
        response.json.contacts.size() == 2 // two merge groups
        response.json.contacts.every { it.merges != null }

        when: "use the returned merge groups to merge duplicates"
        Map contactInfo = response.json.contacts[0]
        int numMergedIn = contactInfo.merges.mergeWith[0].size()
        List mergedInIds = contactInfo.merges.mergeWith[0].collect { it.id }
        List thisMergeActions = [
            [
                action:Constants.MERGE_ACTION_DEFAULT,
                mergeIds:mergedInIds
            ]
        ]
        response = rest.put("$baseUrl/v1/contacts/${contactInfo.id}") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                contact {
                    doMergeActions = thisMergeActions
                }
            }
        }
        int afterNumContacts = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            return s1.phone.contacts.size()
        }.curry(loggedInUsername))

        then: "merge success and merged-in do not show up because they are marked as deleted"
        response.status == OK.value()
        response.json.contact instanceof Map
        response.json.contact.id == contactInfo.id
        afterNumContacts == totalNumContacts - numMergedIn
        mergedInIds.every { Long mergedInId ->
            remote.exec({ Long cId -> Contact.get(cId).isDeleted == true }.curry(mergedInId))
        }
    }
}
