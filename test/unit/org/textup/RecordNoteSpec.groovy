package org.textup

import com.amazonaws.HttpMethod
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.types.ReceiptStatus
import org.textup.validator.PhoneNumber
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Domain([Record, RecordItem, RecordText, RecordCall, RecordNote,
	RecordNoteRevision, RecordItemReceipt, Location])
@TestMixin(HibernateTestMixin)
@Unroll
class RecordNoteSpec extends Specification {

    Record _rec
    String _urlRoot = "http://www.example.com/?key="
    int _maxNumImages = 1

    void setup() {
        _rec = new Record()
        assert _rec.save(flush:true, failOnError:true)

        RecordNote.metaClass.constructor = { Map props ->
            RecordNote note1 = new RecordNote()
            note1.properties = props
            note1.grailsApplication = [getFlatConfig:{
                ['textup.maxNumImages':_maxNumImages]
            }] as GrailsApplication
            note1.storageService = [generateAuthLink:{
                String k, HttpMethod v, Map m=[:] ->
                new Result(success:true, payload:new URL("${_urlRoot}${k}"))
            }] as StorageService
            note1
        }
    }

    void "test validation"() {
    	when: "empty note"
        RecordNote note1 = new RecordNote(record:_rec)

    	then: "a completely empty note is valid"
        note1.validate() == true

    	when: "contents too long"
        int maxLength = 1000
        note1.contents = (0..(maxLength * 2))
            .inject("") { String accum, Integer rangeCount -> accum + "a" }

    	then:
        note1.validate() == false
        note1.errors.errorCount == 1
        note1.errors.getFieldErrorCount("contents") == 1

    	when: "too many images"
        note1.contents = null
        (_maxNumImages * 2).times { note1.addImage("mimeType", 0L) }

    	then:
        note1.validate() == false
        note1.errors.errorCount == 1
        note1.errors.getFieldErrorCount("imageKeysAsString") == 1
    }

    void "test images"() {
    	given: "a valid note"
        RecordNote note1 = new RecordNote(record:_rec)
        assert note1.validate()

    	when: "add an image"
        assert note1.addImage("mimeType", 0L) instanceof String
        assert note1.validate()

    	then:
        note1.imageKeys.size() == 1
        note1.images.size() == 1

    	when: "remove an image that doesn't exist"
        String imageKey = note1.imageKeys[0]
        assert note1.removeImage("blah") == null

    	then:
        note1.imageKeys.size() == 1
        note1.images.size() == 1

    	when: "remove an image that does exist"
        assert note1.removeImage(imageKey) == imageKey

    	then:
        note1.imageKeys.size() == 0
        note1.images.size() == 0
    }

    void "test creating a revision"() {
    	given: "a valid note"
        RecordNote note1 = new RecordNote(record:_rec)
        assert note1.validate()

    	when: "creating a revision"
        RecordNoteRevision rev1 = note1.createRevision()

    	then:
        rev1.authorName == note1.authorName
        rev1.authorId == note1.authorId
        rev1.authorType == note1.authorType
        rev1.whenChanged == note1.whenChanged
        rev1.contents == note1.contents
        rev1.location == note1.location
        rev1.imageKeysAsString == note1.imageKeysAsString
    }
}
