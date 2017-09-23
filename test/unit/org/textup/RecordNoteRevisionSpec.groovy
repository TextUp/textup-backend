package org.textup

import com.amazonaws.services.s3.model.PutObjectResult
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.nio.charset.StandardCharsets
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.type.ReceiptStatus
import org.textup.validator.ImageInfo
import org.textup.validator.PhoneNumber
import org.textup.validator.UploadItem
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Domain([Record, RecordItem, RecordText, RecordCall, RecordNote,
	RecordNoteRevision, RecordItemReceipt, Location])
@TestMixin(HibernateTestMixin)
class RecordNoteRevisionSpec extends Specification {

    Record _rec
    UploadItem _uItem
    String _urlRoot = "http://www.example.com/?key="
    int _maxNumImages = 1

    void setup() {
        _rec = new Record()
        assert _rec.save(flush:true, failOnError:true)

        String contentType = "image/png"
        String data = Base64.encodeBase64String("hello".getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(data)
        _uItem = new UploadItem()
        _uItem.mimeType = contentType
        _uItem.data = data
        _uItem.checksum = checksum
        assert _uItem.validate()

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
            clazz.storageService = [
                generateAuthLink:{ String k ->
                    new Result(success:true, payload:new URL("${_urlRoot}${k}"))
                },
                upload: { String objectKey, UploadItem uItem ->
                    new Result(success:true, payload:[getETag: { -> _eTag }] as PutObjectResult)
                }
            ] as StorageService
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

        when: "noteContents too long"
        int maxLength = 1000
        rev1.noteContents = (0..(maxLength * 2))
            .inject("") { String accum, Integer rangeCount -> accum + "a" }

        then:
        rev1.validate() == false
        rev1.errors.errorCount == 1
        rev1.errors.getFieldErrorCount("noteContents") == 1

        when: "too many images"
        note1.imageKeysAsString = null // clear images
        (_maxNumImages * 2).times {
            note1.addImage(_uItem)
        }
        rev1.noteContents = null
        rev1.imageKeysAsString = note1.imageKeysAsString

        then:
        rev1.validate() == false
        rev1.errors.errorCount == 1
        rev1.errors.getFieldErrorCount("imageKeysAsString") == 1
        rev1.errors
            .getFieldError("imageKeysAsString")
            .codes.contains("recordNoteRevision.imageKeysAsString.tooMany")

        when: "duplicate image keys"
        note1.setImageKeys(['same key', 'same key'])
        rev1.imageKeysAsString = note1.imageKeysAsString


        then:
        rev1.validate() == false
        rev1.errors.errorCount == 1
        rev1.errors.getFieldErrorCount("imageKeysAsString") == 1
        rev1.errors
            .getFieldError("imageKeysAsString")
            .codes.contains("recordNoteRevision.imageKeysAsString.duplicates")
    }

    void "getting image keys or links"() {
    	given: "a valid revision for a valid note"
        RecordNote note1 = new RecordNote(record:_rec)
        assert note1.save(flush:true, failOnError:true)
        RecordNoteRevision rev1 = new RecordNoteRevision(note:note1,
            whenChanged:note1.whenChanged)
        assert rev1.save(flush:true, failOnError:true)

    	when: "adding an image"
        note1.imageKeysAsString = null // clear images
        note1.addImage(_uItem)
        rev1.imageKeysAsString = note1.imageKeysAsString
        assert rev1.save(flush:true, failOnError:true)

    	then: "getting image links and keys"
        rev1.imageKeys.size() == 1
        rev1.imageKeys[0] instanceof String
        rev1.images.size() == 1
        rev1.images[0] instanceof ImageInfo
    }
}
