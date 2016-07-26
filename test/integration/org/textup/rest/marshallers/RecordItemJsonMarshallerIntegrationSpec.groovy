package org.textup.rest.marshallers

import grails.converters.JSON
import org.textup.*
import org.textup.types.AuthorType
import org.textup.types.ReceiptStatus
import org.textup.types.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.Author
import org.textup.validator.TempRecordReceipt

class RecordItemJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    protected boolean validate(Map json, RecordItem item) {
        assert json.id == item.id
        assert json.whenCreated == item.whenCreated.toString()
        assert json.outgoing == item.outgoing
        assert json.contact == Contact.findByRecord(item.record).id
        assert json.hasAwayMessage == item.hasAwayMessage
        assert json.isAnnouncement == item.isAnnouncement
        assert json.contact != null || json.tag != null
        if (json.contact) {
            assert Contact.exists(json.contact)
        }
        else if (json.tag) {
            assert ContactTag.exists(json.tag)
        }
        true
    }

    void "test marshalling call with author and receipts"() {
        given: "call"
        Author author = new Author(id:s1.id, name:s1.name, type:AuthorType.STAFF)
        RecordCall rCall1 = c1.record.addCall([:], author).payload
        TempRecordReceipt r1 = new TempRecordReceipt(status:ReceiptStatus.BUSY,
            apiId:"apiId", receivedByAsString:"1112223333")
        assert r1.validate()
        rCall1.addReceipt(r1)
        rCall1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(rCall1 as JSON) as Map
    	}

    	then:
    	validate(json, rCall1)
        json.type == RecordItemType.CALL.toString()
        json.durationInSeconds == rCall1.durationInSeconds
        json.hasVoicemail == rCall1.hasVoicemail
        json.authorName == rCall1.authorName
        json.authorId == rCall1.authorId
        json.authorType == rCall1.authorType.toString()
        json.receipts instanceof List
        json.receipts.size() == rCall1.receipts.size()
        json.receipts[0].id != null
        json.receipts[0].status == r1.status.toString()
        json.receipts[0].receivedBy == r1.receivedBy.e164PhoneNumber
    }

    void "test marshalling text without author or receipts"() {
        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(rText1 as JSON) as Map
        }

        then:
        validate(json, rText1)
        json.type == RecordItemType.TEXT.toString()
        json.contents == rText1.contents
        json.authorName == null
        json.authorId == null
        json.authorType == null
        json.receipts instanceof List
        json.receipts.size() == (rText1.receipts ? rText1.receipts.size() : 0)
    }
}
