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

    void "test marshalling revision"() {
        given: "revision"
        Record rec1 = TestUtils.buildRecord()
        RecordNote rNote1 = new RecordNote(record: rec1, noteContents: TestUtils.randString())
        rNote1.save(flush:true, failOnError:true)
        RecordNoteRevision rev1 = new RecordNoteRevision(note: rNote1,
            whenChanged: DateTime.now(),
            location: TestUtils.buildLocation(),
            authorName: TestUtils.randString(),
            authorId: TestUtils.randIntegerUpTo(88, true),
            authorType: AuthorType.STAFF,
            noteContents: TestUtils.randString(),
            media: TestUtils.buildMediaInfo())
        rev1.save(flush:true, failOnError:true)

    	when:
        RequestUtils.trySet(RequestUtils.TIMEZONE, 1234)
    	Map json = TestUtils.objToJsonMap(rev1)

    	then:
        json.id != null
    	new DateTime(json.whenChanged) == rev1.whenChanged
        json.noteContents == rev1.noteContents
        json.location instanceof Map
        json.media instanceof Map
        json.authorName == rev1.authorName
        json.authorId == rev1.authorId
        json.authorType == rev1.authorType.toString()
        json.whenChanged.contains("Z")

        when: "add timezone"
        String tzId = "Europe/Stockholm"
        RequestUtils.trySet(RequestUtils.TIMEZONE, tzId)
        json = TestUtils.objToJsonMap(rev1)

        then:
        json.whenChanged.contains("+01:00")
    }
}
