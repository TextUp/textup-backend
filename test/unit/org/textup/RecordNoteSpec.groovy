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
import org.textup.types.ReceiptStatus
import org.textup.validator.PhoneNumber
import org.textup.validator.UploadItem
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
    UploadItem _uItem
    String _urlRoot = "http://www.example.com/?key="
    int _maxNumImages = 1
    String _eTag = UUID.randomUUID().toString()

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

        RecordNote.metaClass.constructor = { Map props ->
            RecordNote note1 = new RecordNote()
            note1.properties = props
            note1.grailsApplication = [getFlatConfig:{
                ['textup.maxNumImages':_maxNumImages]
            }] as GrailsApplication
            note1.storageService = [
                generateAuthLink:{ String k ->
                    new Result(success:true, payload:new URL("${_urlRoot}${k}"))
                },
                upload: { String objectKey, UploadItem uItem ->
                    new Result(success:true, payload:[getETag: { -> _eTag }] as PutObjectResult)
                }
            ] as StorageService
            note1
        }
    }

    void "test validation"() {
    	when: "empty note"
        RecordNote note1 = new RecordNote(record:_rec)

    	then: "a completely empty note is valid"
        note1.validate() == true

    	when: "noteContents too long"
        int maxLength = 1000
        note1.noteContents = (0..(maxLength * 2))
            .inject("") { String accum, Integer rangeCount -> accum + "a" }

    	then:
        note1.validate() == false
        note1.errors.errorCount == 1
        note1.errors.getFieldErrorCount("noteContents") == 1

    	when: "too many images"
        note1.noteContents = null
        (_maxNumImages * 2).times {
            note1.addImage(_uItem)
        }

    	then:
        note1.validate() == false
        note1.errors.errorCount == 1
        note1.errors.getFieldErrorCount("imageKeysAsString") == 1
        note1.errors
            .getFieldError("imageKeysAsString")
            .codes.contains("recordNote.imageKeysAsString.tooMany")

        when: "duplicate image keys"
        note1.setImageKeys(['same key', 'same key'])

        then:
        note1.validate() == false
        note1.errors.errorCount == 1
        note1.errors.getFieldErrorCount("imageKeysAsString") == 1
        note1.errors
            .getFieldError("imageKeysAsString")
            .codes.contains("recordNote.imageKeysAsString.duplicates")
    }

    void "test images"() {
    	given: "a valid note"
        RecordNote note1 = new RecordNote(record:_rec)
        assert note1.validate()

    	when: "add an image"
        assert note1.addImage(_uItem).payload.getETag() == _eTag
        assert note1.save(flush:true, failOnError:true)

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

    	when: "creating a revision for an unsaved note"
        RecordNoteRevision rev1 = note1.createRevision()

    	then: "null because revisions use PERSISTED values"
        rev1.authorName == null
        rev1.authorId == null
        rev1.authorType == null
        rev1.whenChanged == null
        rev1.noteContents == null
        rev1.location == null
        rev1.imageKeysAsString == null

        when: "creating a revision for a saved note"
        note1.removeFromRevisions(rev1)
        rev1.discard()
        note1.save(flush:true, failOnError:true)
        rev1 = note1.createRevision()

        then:
        rev1.authorName == note1.authorName
        rev1.authorId == note1.authorId
        rev1.authorType == note1.authorType
        rev1.whenChanged == note1.whenChanged
        rev1.noteContents == note1.noteContents
        rev1.location == note1.location
        rev1.imageKeysAsString == note1.imageKeysAsString
    }
}
