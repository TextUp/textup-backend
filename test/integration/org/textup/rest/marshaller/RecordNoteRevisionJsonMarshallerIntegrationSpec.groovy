package org.textup.rest.marshaller

import grails.converters.JSON
import org.joda.time.DateTime
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

class RecordNoteRevisionJsonMarshallerIntegrationSpec extends Specification {

    def grailsApplication

    void "test marshalling revision"() {
        given: "revision"
        Record rec = new Record()
        rec.save(flush:true, failOnError:true)
        RecordNote note1 = new RecordNote(record:rec, noteContents:"note contents!!")
        note1.save(flush:true, failOnError:true)
        RecordNoteRevision rev1 = new RecordNoteRevision(note: note1,
            whenChanged: DateTime.now(),
            location: new Location(address: "hi", lat: 0G, lon: 0G),
            authorName: "hi",
            authorId: 8L,
            authorType: AuthorType.STAFF,
            noteContents: "hi",
            media: new MediaInfo())
        rev1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = TestUtils.jsonToMap(rev1 as JSON)
    	}

    	then:
        json.id != null
    	new DateTime(json.whenChanged) == rev1.whenChanged
        json.noteContents == rev1.noteContents
        json.location instanceof Map
        json.media instanceof Map
        json.authorName == rev1.authorName
        json.authorId == rev1.authorId
        json.authorType == rev1.authorType.toString()
        !json.whenChanged.contains("+01:00")

        when: "add timezone"
        String tzId = "Europe/Stockholm"
        RequestUtils.trySet(Constants.REQUEST_TIMEZONE, tzId)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(rev1 as JSON)
        }

        then:
        json.whenChanged.contains("+01:00")
    }
}
