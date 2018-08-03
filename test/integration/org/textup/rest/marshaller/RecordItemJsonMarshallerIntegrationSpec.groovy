package org.textup.rest.marshaller

import grails.converters.JSON
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.textup.*
import org.textup.type.AuthorType
import org.textup.type.ReceiptStatus
import org.textup.type.RecordItemType
import org.textup.util.TestHelpers
import org.textup.validator.Author
import org.textup.validator.TempRecordReceipt
import spock.lang.*

class RecordItemJsonMarshallerIntegrationSpec extends Specification {

    def grailsApplication
    Record rec

    def setup() {
    	rec = new Record()
        rec.save(flush: true, failOnError: true)
    }

    protected boolean validate(Map json, RecordItem item) {
        assert json.id == item.id
        assert json.whenCreated == item.whenCreated.toString()
        assert json.outgoing == item.outgoing
        assert json.hasAwayMessage == item.hasAwayMessage
        assert json.isAnnouncement == item.isAnnouncement
        assert json.receipts instanceof Map
        assert json.media instanceof Map
        assert json.authorName == item.authorName
        assert json.authorId == item.authorId
        assert json.authorType == item.authorType.toString()
        assert json.noteContents == item.noteContents
        // did not mock up contacts or tags so these both should be null
        assert json.contact == null
        assert json.tag == null
        true
    }

    void "test marshalling voicemail"() {
        given: "call"
        RecordCall rCall1 = new RecordCall(record: rec,
            durationInSeconds: 88,
            voicemailInSeconds: 12,
            hasAwayMessage: true,
            noteContets: "hello",
            authorName: "yes",
            authorId: 88L,
            authorType: AuthorType.STAFF,
            media: new MediaInfo())
        rCall1.addToReceipts(TestHelpers.buildReceipt(ReceiptStatus.BUSY))
        rCall1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = TestHelpers.jsonToMap(rCall1 as JSON)
    	}

    	then:
    	validate(json, rCall1)
        json.durationInSeconds == rCall1.durationInSeconds
        json.hasVoicemail == true
        json.voicemailUrl == json.voicemailUrl
        json.voicemailInSeconds == json.voicemailInSeconds
        json.type == RecordItemType.CALL.toString()
    }

    void "test marshalling call without voicemail"() {
        given: "call"
        RecordCall rCall1 = new RecordCall(record: rec,
            durationInSeconds: 88,
            voicemailInSeconds: 0,
            hasAwayMessage: false,
            noteContets: "hello",
            authorName: "yes",
            authorId: 88L,
            authorType: AuthorType.STAFF,
            media: new MediaInfo())
        rCall1.addToReceipts(TestHelpers.buildReceipt(ReceiptStatus.BUSY))
        rCall1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(rCall1 as JSON)
        }

        then:
        validate(json, rCall1)
        json.durationInSeconds == rCall1.durationInSeconds
        json.hasVoicemail == false
        json.voicemailUrl == null
        json.voicemailInSeconds == null
        json.type == RecordItemType.CALL.toString()
    }

    void "test marshalling text"() {
        given:
        RecordText rText1 = new RecordText(record: rec,
            contents: "hope you're having a great day today!",
            noteContets: "hello",
            authorName: "yes",
            authorId: 88L,
            authorType: AuthorType.STAFF,
            media: new MediaInfo())
        rText1.addToReceipts(TestHelpers.buildReceipt(ReceiptStatus.BUSY))
        rText1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(rText1 as JSON)
        }

        then:
        validate(json, rText1)
        json.contents == rText1.contents
        json.type == RecordItemType.TEXT.toString()
    }

    void "test marshalling note with revisions, location, images, upload links"() {
        given: "note with revisions, location, images, upload links"
        RecordNote note1 = new RecordNote(record: rec,
            noteContets: "i am note contents",
            authorName: "yes",
            authorId: 88L,
            authorType: AuthorType.STAFF,
            media: new MediaInfo(),
            location: new Location(address: "hi", lat: 0G, lon: 0G))
        note1.save(flush:true, failOnError:true)
        //revision
        note1.authorName = "Kiki"
        note1.tryCreateRevision()
        note1.save(flush: true, failOnError: true)
        assert note1.revisions?.size() == 1
        //upload links
        HttpServletRequest request = WebUtils.retrieveGrailsWebRequest().currentRequest
        List<String> errorMessages = ["error1", "error2"]
        request.setAttribute(Constants.REQUEST_UPLOAD_ERRORS, errorMessages)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(note1 as JSON)
        }

        then:
        validate(json, note1)
        json.whenChanged == note1.whenChanged.toString()
        json.isDeleted == note1.isDeleted
        json.isReadOnly == note1.isReadOnly
        json.revisions instanceof List
        json.revisions.size() == 1
        json.location instanceof Map
        json.location.id == note1.location.id

        json.uploadErrors instanceof List
        json.uploadErrors.size() == errorMessages.size()
        errorMessages.every { String msg -> json.uploadErrors.find { it.contains(msg) } }
    }
}
