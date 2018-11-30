package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.springframework.validation.Errors
import org.textup.type.*
import org.textup.validator.UploadItem
import spock.lang.*

@Domain([MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MediaElementSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test constraints"() {
        when: "empty obj"
        MediaElement e1 = new MediaElement()

        then: "send version is no longer mandatory because we asychronously process media"
        e1.validate() == true

        when: "filled in"
        e1.sendVersion = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: UUID.randomUUID().toString(),
            sizeInBytes: Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES / 2,
            widthInPixels: 888)
        assert e1.sendVersion.validate()

        then: "valid, note we only need send version not display versions"
        e1.validate() == true

        when: "size of the send version is too large"
        e1.sendVersion.sizeInBytes = Constants.MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES * 2

        then: "invalid"
        e1.validate() == false
        e1.errors.errorCount == 1
        e1.errors.getFieldErrorCount("sendVersion") == 1
    }

    void "test getting types and versions"() {
        given:
        IOCUtils.metaClass."static".getResultFactory = TestUtils.getResultFactory(grailsApplication)
        byte[] inputData1 = TestUtils.getJpegSampleData512()
        UploadItem uItem1 = new UploadItem(type: MediaType.IMAGE_JPEG, data: inputData1)

        when: "empty obj"
        MediaElement e1 = new MediaElement()

        then:
        e1.allTypes.isEmpty()
        e1.allVersions == []

        when: "adding send version"
        e1.sendVersion = uItem1.toMediaElementVersion()

        then: "obj's send version is set + versions for alternate falls back to send version"
        e1.sendVersion != null
        e1.alternateVersions == null
        e1.allVersions.size() == 1

        e1.allTypes.size() == 1
        e1.allTypes.toArray()[0] == MediaType.IMAGE_JPEG
        e1.hasType([MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG]) == true
        e1.hasType([MediaType.AUDIO_MP3]) == false
        e1.hasType([]) == false
        e1.hasType(null) == false

        when: "adding alternate version"
        e1.addToAlternateVersions(uItem1.toMediaElementVersion())
        uItem1.type = MediaType.IMAGE_PNG
        e1.addToAlternateVersions(uItem1.toMediaElementVersion())

        then: "added to alternate version relationship versions for alternate includes newly added"
        e1.alternateVersions.size() == 2
        e1.allVersions.size() == 3

        e1.allTypes.size() == 2
        e1.allTypes.toArray().every { it == MediaType.IMAGE_JPEG || it == MediaType.IMAGE_PNG }
        e1.hasType([MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG]) == true
        e1.hasType([MediaType.AUDIO_MP3]) == false
        e1.hasType([]) == false
        e1.hasType(null) == false
    }

    void "test cascading validation for single association"() {
        given: "obj with some versions"
        MediaElementVersion mVers = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        assert mVers.validate()
        MediaElement e1 = new MediaElement(sendVersion: mVers)

        when: "we make the version invalid"
        mVers.type = null
        assert mVers.validate() == false

        then: "validating parent reveals invalid version"
        e1.validate() == false
        e1.errors.errorCount == 1
        e1.errors.getFieldErrorCount("sendVersion.type") == 1

        when: "invalid version is fixed"
        mVers.type = MediaType.IMAGE_JPEG
        assert mVers.validate()

        then: "parent validates"
        e1.validate() == true
    }

    void "test cascading validation for hasMany association"() {
        given: "obj with some versions"
        MediaElementVersion sVers = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        MediaElementVersion aVers1 = new MediaElementVersion(type: MediaType.IMAGE_PNG,
            versionId: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        assert sVers.validate()
        assert aVers1.validate()
        MediaElement e1 = new MediaElement(sendVersion: sVers)
        e1.addToAlternateVersions(aVers1)

        when: "we make the version invalid"
        aVers1.type = null
        assert aVers1.validate() == false

        then: "validating parent reveals invalid version"
        e1.validate() == false
        e1.errors.getFieldErrorCount("alternateVersions.0.type") == 1

        when: "invalid version is fixed"
        aVers1.type = MediaType.IMAGE_PNG
        assert aVers1.validate()

        then: "parent validates"
        e1.validate() == true
    }

    void "test static creation method"() {
        given:
        IOCUtils.metaClass."static".getResultFactory = TestUtils.getResultFactory(grailsApplication)
        UploadItem mockSendItem = Mock(UploadItem)
        UploadItem mockAltItem = Mock(UploadItem)

        when: "create with no items"
        Result<MediaElement> res = MediaElement.create(null, null)

        then: "valid with no items"
        res.status == ResultStatus.OK
        res.payload instanceof MediaElement
        res.payload.allVersions.isEmpty()

        when: "create with valid content type and a send version"
        res = MediaElement.create(mockSendItem, [mockAltItem])

        then: "valid -- see mocks"
        1 * mockSendItem.toMediaElementVersion() >> TestUtils.buildMediaElementVersion()
        1 * mockAltItem.toMediaElementVersion() >> TestUtils.buildMediaElementVersion()
        res.success == true
        res.payload instanceof MediaElement
        res.payload.validate()
        res.payload.allVersions.size() == 2
        res.payload.sendVersion != null
        res.payload.alternateVersions.size() == 1
    }
}
