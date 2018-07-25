package org.textup.rest.marshaller

import grails.converters.JSON
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.textup.*
import org.textup.type.AuthorType
import org.textup.type.ReceiptStatus
import org.textup.type.RecordItemType
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
            apiId:"apiId", contactNumberAsString:"1112223333")
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
        json.receipts[0].contactNumber == r1.contactNumber.e164PhoneNumber
    }

    void "test marshalling text without author or receipts"() {
        given: "text without author or receipts"
        assert rText1.receipts == null
        assert rText1.authorName == null
        assert rText1.authorId == null
        assert rText1.authorType == null

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
        json.receipts == null
    }

    void "test marshalling note with revisions, location, images, upload links"() {
        given: "note with revisions, location, images, upload links"
        RecordNote note1 = new RecordNote(record:c1.record, noteContents:"i am note contents")
        //images
        Collection<String> imageKeys = ["key1", "key2"]
        note1.setImageKeys(imageKeys)
        note1.save(flush:true, failOnError:true)
        //revision
        RecordNoteRevision rev1 = note1.createRevision()
        rev1.save(flush:true, failOnError:true)
        //location
        note1.location = new Location(address:"hi", lat:8G, lon:8G)
        note1.location.save(flush:true, failOnError:true)
        //upload links
        HttpServletRequest request = WebUtils.retrieveGrailsWebRequest().currentRequest
        List<String> errorMessages = ["error1", "error2"]
        request.setAttribute(Constants.UPLOAD_ERRORS, errorMessages)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(note1 as JSON) as Map
        }

        then:
        validate(json, note1)
        json.whenChanged == note1.whenChanged.toString()
        json.isDeleted == note1.isDeleted
        json.revisions instanceof List
        json.revisions.size() == 1
        json.noteContents == note1.noteContents
        json.location instanceof Map
        json.location.id == note1.location.id
        json.images instanceof List
        json.images.size() == imageKeys.size()
        imageKeys.every { String key -> json.images.find { it.key.contains(key) } }
        json.uploadErrors instanceof List
        json.uploadErrors.size() == errorMessages.size()
        errorMessages.every { String msg -> json.uploadErrors.find { it.contains(msg) } }
    }
}
