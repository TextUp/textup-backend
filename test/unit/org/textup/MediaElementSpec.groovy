package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.springframework.validation.Errors
import org.textup.type.*
import org.textup.util.TestHelpers
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

        then: "invalid"
        e1.validate() == false

        when: "filled in"
        e1.type = MediaType.IMAGE_JPEG
        e1.sendVersion = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
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

    void "test adding and retrieving versions"() {
        given: "empty obj"
        Helpers.metaClass.'static'.getResultFactory = TestHelpers.getResultFactory(grailsApplication)
        MediaElement e1 = new MediaElement()
        byte[] inputData1 = TestHelpers.getJpegSampleData512()

        when: "adding send version"
        e1.addVersion([
            new UploadItem(mediaVersion: MediaVersion.SEND,
                type: MediaType.IMAGE_JPEG,
                data: inputData1)
        ])

        then: "obj's send version is set + versions for display falls back to send version"
        e1.sendVersion != null
        e1.displayVersions == null
        e1.versionsForDisplay.size() == 1
        e1.versionsForDisplay.containsKey(MediaVersion.LARGE)
        e1.versionsForDisplay[MediaVersion.LARGE].mediaVersion == MediaVersion.SEND

        when: "adding display version"
        e1.addVersion([
            new UploadItem(mediaVersion: MediaVersion.MEDIUM,
                type: MediaType.IMAGE_JPEG,
                data: inputData1)
        ])

        then: "added to display version relationship versions for display includes newly added"
        e1.displayVersions.size() == 1
        e1.versionsForDisplay.size() == 1
        e1.versionsForDisplay.containsKey(MediaVersion.MEDIUM)
    }

    void "test cascading validation for single association"() {
        given: "obj with some versions"
        MediaElementVersion mVers = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        assert mVers.validate()
        MediaElement e1 = new MediaElement(type: MediaType.IMAGE_JPEG, sendVersion: mVers)

        when: "we make the version invalid"
        mVers.mediaVersion = null
        assert mVers.validate() == false

        then: "validating parent reveals invalid version"
        e1.validate() == false
        e1.errors.errorCount == 1
        e1.errors.getFieldErrorCount('sendVersion.mediaVersion') == 1

        when: "invalid version is fixed"
        mVers.mediaVersion = MediaVersion.SEND
        assert mVers.validate()

        then: "parent validates"
        e1.validate() == true
    }

    void "test cascading validation for hasMany association"() {
        given: "obj with some versions"
        MediaElementVersion sVers = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        MediaElementVersion dVers = new MediaElementVersion(mediaVersion: MediaVersion.MEDIUM,
            key: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        assert sVers.validate()
        assert dVers.validate()
        MediaElement e1 = new MediaElement(type: MediaType.IMAGE_JPEG, sendVersion: sVers)
        e1.addToDisplayVersions(dVers)

        when: "we make the version invalid"
        dVers.mediaVersion = null
        assert dVers.validate() == false

        then: "validating parent reveals invalid version"
        e1.validate() == false
        e1.errors.getFieldErrorCount('displayVersions.0.mediaVersion') == 1

        when: "invalid version is fixed"
        dVers.mediaVersion = MediaVersion.SEND
        assert dVers.validate()

        then: "parent validates"
        e1.validate() == true
    }

    void "test static creation method"() {
        given:
        Helpers.metaClass.'static'.getResultFactory = TestHelpers.getResultFactory(grailsApplication)
        UploadItem mockUploadItem = Mock(UploadItem)

        when: "create with no items"
        Result<MediaElement> res = MediaElement.create(MediaType.IMAGE_PNG, [])

        then: "invalid -- see mocks"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "nullable"

        when: "create with valid content type and a send version"
        res = MediaElement.create(MediaType.IMAGE_JPEG, [mockUploadItem])

        then: "valid -- see mocks"
        (1.._) * mockUploadItem.getMediaVersion() >> MediaVersion.SEND
        1 * mockUploadItem.getKey() >> "key"
        1 * mockUploadItem.getSizeInBytes() >> 88
        1 * mockUploadItem.getWidthInPixels() >> 88
        1 * mockUploadItem.getHeightInPixels() >> 88
        res.success == true
        res.payload instanceof MediaElement
        res.payload.validate()
        res.payload.sendVersion != null
    }
}
