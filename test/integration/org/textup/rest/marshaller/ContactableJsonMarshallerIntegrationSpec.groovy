package org.textup.rest.marshaller

import grails.converters.JSON
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.ContactStatus
import org.textup.util.*

class ContactableJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    protected boolean validate(Map json, Contactable c1) {
        assert json.id == c1.contactId
        assert json.lastRecordActivity == c1.tryGetRecord().payload.lastRecordActivity.toString()
        assert json.name == c1.name
        assert json.note == c1.note
        assert json.numbers instanceof List
        assert json.language == c1.tryGetRecord().payload.language.toString()
        assert json.numbers.size() == (c1.numbers ? c1.numbers.size() : 0)
        c1.numbers?.each { ContactNumber num ->
            assert json.numbers.find { it.number == num.prettyPhoneNumber }
        }
        assert json.futureMessages instanceof List
        assert json.futureMessages.size() == (c1.tryGetRecord().payload.futureMessages?.size() ?: 0)
        c1.tryGetRecord().payload.futureMessages?.each { FutureMessage fMsg ->
            assert json.futureMessages.find { it.id == fMsg.id }
        }
        true
    }

    void "test marshalling contact"() {
        given:
        IncomingSession sess1 = new IncomingSession(phone:c1.phone,
            numberAsString:c1.numbers[0].number)
        sess1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = TestUtils.jsonToMap(c1 as JSON)
    	}

    	then:
        validate(json, c1)
        json.status == c1.status.toString()
        json.tags instanceof List
        json.tags.size() == c1.tags.size()
        c1.tags.every { ContactTag ct1 -> json.tags.find { it.id == ct1.id } }
        json.sessions instanceof List
        json.sessions.size() == c1.sessions.size()
        c1.sessions.every { IncomingSession session ->
            json.sessions.find { it.id == session.id }
        }
        json.sharedWith instanceof List
        json.sharedWith.size() == c1.sharedContacts.size()
        c1.sharedContacts.every { SharedContact sc1 ->
            json.sharedWith.find { it.id == sc1.id }
        }
    }

    void "test marshalling shared contact"() {
        given: "different statuses for contact and shared contact"
        sc2.contact.status = ContactStatus.ARCHIVED
        sc2.status = ContactStatus.UNREAD
        [sc2, sc2.contact]*.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(sc2 as JSON)
        }

        then:
        validate(json, sc2)
        json.status == sc2.status.toString()
        json.status != sc2.contact.status.toString()
        json.permission == sc2.permission.toString()
        json.startedSharing == sc2.whenCreated.toString()
        json.sharedBy == sc2.sharedBy.name
        json.sharedById == sc2.sharedBy.id
    }

    void "test marshalling unread contactable and detailed unread info"() {
        given: "a contactable NOT UNREAD with last touched before some record items"
        c1.lastTouched = DateTime.now()
        c1.status = ContactStatus.ACTIVE
        DateTime dtInFuture = DateTime.now().plusDays(2)
        RecordText rText1 = c1.record.storeOutgoingText("text").payload
        RecordCall rCall1 = c1.record.storeOutgoingCall().payload
        rCall1.whenCreated = dtInFuture
        [c1, rText1, rCall1]*.save(flush: true, failOnError: true)

        when: "we marshal this contactable"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(c1 as JSON)
        }

        then: "detailed unread info DOES NOT show up because status is not unread"
        json.unreadInfo == null

        when: "we update status to be unread"
        c1.status = ContactStatus.UNREAD
        c1.save(flush: true, failOnError: true)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(c1 as JSON)
        }

        then: "detailed unread info shows up"
        json.unreadInfo instanceof Map
        json.unreadInfo.numTexts == 1
        json.unreadInfo.numCalls == 1
        json.unreadInfo.numVoicemails == 0

        when: "we update last touched so it is after all record items"
        c1.lastTouched = dtInFuture.plusDays(2)
        c1.save(flush: true, failOnError: true)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(c1 as JSON)
        }

        then: "no detailed unread info even if status is unread"
        json.unreadInfo == null
    }
}
