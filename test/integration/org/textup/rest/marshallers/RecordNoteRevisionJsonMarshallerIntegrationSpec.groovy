package org.textup.rest.marshallers

import grails.converters.JSON
import org.textup.*
import org.textup.types.AuthorType
import org.textup.types.ReceiptStatus
import org.textup.types.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.Author
import org.textup.validator.TempRecordReceipt

class RecordNoteRevisionJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling revision"() {
        given: "revision"
        Collection<String> imageKeys = ["key1", "key2"]
        RecordNote note1 = new RecordNote(record:c1.record)
        note1.setImageKeys(imageKeys)
        note1.save(flush:true, failOnError:true)
        RecordNoteRevision rev1 = note1.createRevision()
        rev1.save(flush:true, failOnError:true)

    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(rev1 as JSON) as Map
    	}

    	then:
    	json.whenChanged == rev1.whenChanged.toString()
        json.contents == rev1.contents
        json.location == rev1.location
        json.images == rev1.imageLinks
        imageKeys.every({ json.images.toString().contains(it) })
        if (rev1.authorName) json.authorName == rev1.authorName
        if (rev1.authorId) json.authorId == rev1.authorId
        if (rev1.authorType) json.authorType == rev1.authorType.toString()
        RecordNote.exists(rev1.note.id) == true
    }
}
