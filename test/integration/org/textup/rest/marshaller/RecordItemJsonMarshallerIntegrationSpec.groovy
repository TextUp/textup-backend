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
            voicemailInSeconds: 12,
            hasAwayMessage: true,
            noteContents: "hello",
            authorName: "yes",
            authorId: 88L,
            authorType: AuthorType.STAFF,
            media: new MediaInfo())
        RecordItemReceipt rpt1 = TestHelpers.buildReceipt(ReceiptStatus.BUSY)
        rpt1.numBillable = 88
        rCall1.addToReceipts(rpt1)
        rCall1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = TestHelpers.jsonToMap(rCall1 as JSON)
    	}

    	then:
    	validate(json, rCall1)
        json.durationInSeconds == rCall1.durationInSeconds
        json.voicemailInSeconds == json.voicemailInSeconds
        json.type == RecordItemType.CALL.toString()
    }

    void "test marshalling call without voicemail"() {
        given: "call"
        RecordCall rCall1 = new RecordCall(record: rec,
            voicemailInSeconds: 0,
            hasAwayMessage: false,
            noteContents: "hello",
            authorName: "yes",
            authorId: 88L,
            authorType: AuthorType.STAFF,
            media: new MediaInfo())
        RecordItemReceipt rpt1 = TestHelpers.buildReceipt(ReceiptStatus.BUSY)
        rpt1.numBillable = 88
        rCall1.addToReceipts(rpt1)
        rCall1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(rCall1 as JSON)
        }

        then:
        validate(json, rCall1)
        json.durationInSeconds == rCall1.durationInSeconds
        json.voicemailInSeconds == 0
        json.type == RecordItemType.CALL.toString()
    }

    void "test marshalling text"() {
        given:
        RecordText rText1 = new RecordText(record: rec,
            contents: "hope you're having a great day today!",
            noteContents: "hello",
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
            noteContents: "i am note contents",
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
        request.setAttribute(Constants.REQUEST_UPLOAD_ERRORS, ["error1", "error2"])

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(note1 as JSON)
        }

        then: "no upload errors shown here -- see MediaInfo json marshaller"
        validate(json, note1)
        json.whenChanged == note1.whenChanged.toString()
        json.isDeleted == note1.isDeleted
        json.isReadOnly == note1.isReadOnly
        json.revisions instanceof List
        json.revisions.size() == 1
        json.location instanceof Map
        json.location.id == note1.location.id
        json.uploadErrors == null
        json.type == "NOTE"
    }
}
