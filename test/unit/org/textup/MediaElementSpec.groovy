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

    void "test constraints"() {
        when: "empty obj"
        MediaElement e1 = new MediaElement()

        then: "invalid"
        e1.validate() == false

        when: "filled in"
        e1.type = MediaType.IMAGE
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
        Helpers.metaClass.'static'.getResultFactory = { -> mockResultFactory() }
        MediaElement e1 = new MediaElement()
        byte[] inputData1 = TestHelpers.getJpegSampleData512()

        when: "adding send version"
        e1.addVersion([
            new UploadItem(mediaVersion: MediaVersion.SEND,
                mimeType: Constants.MIME_TYPE_JPEG,
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
                mimeType: Constants.MIME_TYPE_JPEG,
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
        MediaElement e1 = new MediaElement(type: MediaType.IMAGE, sendVersion: mVers)

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
        MediaElement e1 = new MediaElement(type: MediaType.IMAGE, sendVersion: sVers)
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
        Helpers.metaClass.'static'.getResultFactory = { -> mockResultFactory() }

        when: "create with no items"
        Result<MediaElement> res = MediaElement.create(Constants.MIME_TYPE_PNG, [])

        then: "invalid -- see mocks"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.payload instanceof Errors
        res.payload.errorCount == 1
        res.payload.getFieldErrorCount("sendVersion") == 1

        when: "create with valid content type and a send version"
        res = MediaElement.create(Constants.MIME_TYPE_JPEG, [
            new UploadItem(mediaVersion: MediaVersion.SEND,
                mimeType: Constants.MIME_TYPE_JPEG,
                data: TestHelpers.getJpegSampleData512())
        ])

        then: "valid -- see mocks"
        res.success == true
        res.payload instanceof MediaElement
        res.payload.validate()
    }

    // Helpers
    // -------

    protected ResultFactory mockResultFactory() {
        [
            success: { Object obj ->
                new Result(payload: obj)
            },
            failWithValidationErrors: { Errors e ->
                new Result(status: ResultStatus.UNPROCESSABLE_ENTITY, payload: e)
            }
        ] as ResultFactory
    }
}
