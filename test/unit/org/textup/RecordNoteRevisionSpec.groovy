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

@Domain([Record, RecordItem, RecordText, RecordCall, RecordNote,
	RecordNoteRevision, RecordItemReceipt, Location])
@TestMixin(HibernateTestMixin)
class RecordNoteRevisionSpec extends Specification {

    Record _rec
    String _urlRoot = "http://www.example.com/?key="
    int _maxNumImages = 1

    void setup() {
        _rec = new Record()
        assert _rec.save(flush:true, failOnError:true)

        RecordNote.metaClass.constructor = mockConstructor(RecordNote)
        RecordNoteRevision.metaClass.constructor = mockConstructor(RecordNoteRevision)
    }
    protected Closure mockConstructor(Class forClass) {
        { Map props ->
            def clazz = forClass.newInstance()
            clazz.properties = props
            clazz.grailsApplication = [getFlatConfig:{
                ['textup.maxNumImages':_maxNumImages]
            }] as GrailsApplication
            clazz.storageService = [generateAuthLink:{
                String k, HttpMethod v, Map m=[:] ->
                new Result(success:true, payload:new URL("${_urlRoot}${k}"))
            }] as StorageService
            clazz.asType(forClass)
        }
    }

    void "test validation"() {
        when: "empty revision"
        RecordNoteRevision rev1 = new RecordNoteRevision([:])

        then: "requires an associated note"
        rev1.validate() == false
        rev1.errors.errorCount == 2
        rev1.errors.getFieldErrorCount('whenChanged') == 1
        rev1.errors.getFieldErrorCount('note') == 1

        when: "associate revision with a note"
        RecordNote note1 = new RecordNote(record:_rec)
        assert note1.validate()
        rev1.note = note1
        rev1.whenChanged = note1.whenChanged

        then:
        rev1.validate() == true

        when: "contents too long"
        int maxLength = 1000
        rev1.contents = (0..(maxLength * 2))
            .inject("") { String accum, Integer rangeCount -> accum + "a" }

        then:
        rev1.validate() == false
        rev1.errors.errorCount == 1
        rev1.errors.getFieldErrorCount("contents") == 1

        when: "too many images"
        rev1.contents = null
        // add images to note so that it can stringify json for us
        (_maxNumImages * 2).times { note1.addImage("mimeType", 0L) }
        // then set this stringified json on the revision
        rev1.imageKeysAsString = note1.imageKeysAsString

        then:
        rev1.validate() == false
        rev1.errors.errorCount == 1
        rev1.errors.getFieldErrorCount("imageKeysAsString") == 1
    }

    void "getting image keys or links"() {
    	given: "a valid revision for a valid note"
        RecordNote note1 = new RecordNote(record:_rec)
        assert note1.validate()
        RecordNoteRevision rev1 = new RecordNoteRevision(note:note1,
            whenChanged:note1.whenChanged)
        assert rev1.validate()

    	when: "adding an image"
        // add images to note so that it can stringify json for us
        note1.addImage("mimeType", 0L)
        // then set this stringified json on the revision
        rev1.imageKeysAsString = note1.imageKeysAsString
        assert rev1.validate()

    	then: "getting image links and keys"
        rev1.imageLinks.size() == 1
        rev1.imageKeys.size() == 1
    }
}
