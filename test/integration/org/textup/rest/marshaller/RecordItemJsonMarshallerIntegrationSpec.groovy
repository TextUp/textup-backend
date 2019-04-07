package org.textup.rest.marshaller

import grails.converters.JSON
import java.util.concurrent.*
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

class RecordItemJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication
    Record rec

    def setup() {
        setupIntegrationData()

        rec = new Record()
        rec.save(flush: true, failOnError: true)
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    protected boolean validate(Map json, RecordItem item) {
        assert json.id == item.id
        assert json.whenCreated == item.whenCreated.toString()
        assert json.outgoing == item.outgoing
        assert json.hasAwayMessage == item.hasAwayMessage
        assert json.isAnnouncement == item.isAnnouncement
        assert json.wasScheduled == item.wasScheduled
        assert json.receipts instanceof Map
        assert json.media instanceof Map
        assert json.authorName == item.authorName
        assert json.authorId == item.authorId
        assert json.authorType == item.authorType.toString()
        assert json.noteContents == item.noteContents
        true
    }

    void "test marshalling with different record owners"() {
        given:
        RecordItem rItem1 = new RecordItem(record: rec)
        Map json

        when: "record owner is a contact"
        c1.record = rec
        c1.save(flush: true, failOnError: true)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(rItem1 as JSON)
        }

        then:
        json.contact == c1.id
        json.ownerName == c1.nameOrNumber
        json.tag == null

        when: "record owner is a tag"
        c1.record = tag1.record
        tag1.record = rec
        tag1.save(flush: true, failOnError: true)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(rItem1 as JSON)
        }

        then:
        json.contact == null
        json.ownerName == tag1.name
        json.tag == tag1.id
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
            media: new MediaInfo(),
            wasScheduled: true)
        RecordItemReceipt rpt1 = TestUtils.buildReceipt(ReceiptStatus.BUSY)
        rpt1.numBillable = 88
        rCall1.addToReceipts(rpt1)
        rCall1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = TestUtils.jsonToMap(rCall1 as JSON)
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
        RecordItemReceipt rpt1 = TestUtils.buildReceipt(ReceiptStatus.BUSY)
        rpt1.numBillable = 88
        rCall1.addToReceipts(rpt1)
        rCall1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(rCall1 as JSON)
        }

        then:
        validate(json, rCall1)
        json.durationInSeconds == rCall1.durationInSeconds
        json.stillOngoing == false
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
        rText1.addToReceipts(TestUtils.buildReceipt(ReceiptStatus.BUSY))
        rText1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(rText1 as JSON)
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
            json = TestUtils.jsonToMap(note1 as JSON)
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

    void "test marshalling note with specified timezone"() {
        given:
        String tzId = "Europe/Stockholm"
        String offsetString = TestUtils.getDateTimeOffsetString(tzId)
        Utils.trySetOnRequest(Constants.REQUEST_TIMEZONE, tzId)

        RecordNote note1 = new RecordNote(record: rec, noteContents: TestUtils.randString())
        note1.save(flush:true, failOnError:true)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(note1 as JSON)
        }

        then:
        json.whenCreated.contains(offsetString)
        json.whenChanged.contains(offsetString)
    }
}
