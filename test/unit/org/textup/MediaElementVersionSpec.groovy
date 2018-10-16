package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.type.*
import spock.lang.*

@Domain([MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MediaElementVersionSpec extends Specification {

    void "test constraints + width and link custom getters"() {
        given: "storage service mock"
        StorageService storageServiceMock = Mock(StorageService)

        when: "empty obj"
        MediaElementVersion mVers = new MediaElementVersion()
        mVers.storageService = storageServiceMock

        then: "invalid + custom getters are null-safe"
        0 * storageServiceMock._
        mVers.validate() == false
        mVers.link == null

        when: "filled in obj"
        mVers.type = MediaType.IMAGE_JPEG
        mVers.versionId = UUID.randomUUID().toString()
        mVers.sizeInBytes = 888
        String link = mVers.link?.toString()

        then: "valid, defaults to being private"
        1 * storageServiceMock.generateAuthLink(*_) >> new Result(payload: new URL("https://www.example.com"))
        0 * storageServiceMock.generateLink(*_)
        mVers.validate() == true
        link == "https://www.example.com"

        when: "is public"
        mVers.isPublic = true
        link = mVers.link?.toString()

        then:
        0 * storageServiceMock.generateAuthLink(*_)
        1 * storageServiceMock.generateLink(*_) >> new Result(payload: new URL("https://www.example.com"))
        mVers.validate() == true
        link == "https://www.example.com"
    }

    void "test constraint errors"() {
        given: "a valid obj"
        MediaElementVersion mVers = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        assert mVers.validate()

        when: "negative file size"
        mVers.sizeInBytes = -10

        then: "invalid"
        mVers.validate() == false
        mVers.errors.errorCount == 1

        when: "zero file size"
        mVers.sizeInBytes = 0

        then: "invalid"
        mVers.validate() == false
        mVers.errors.errorCount == 1

        when: "negative width"
        mVers.widthInPixels = -10

        then: "invalid"
        mVers.validate() == false
        mVers.errors.errorCount == 2

        when: "zero width"
        mVers.widthInPixels = 0

        then: "invalid"
        mVers.validate() == false
        mVers.errors.errorCount == 2

        when: "negative height"
        mVers.heightInPixels = -88

        then:
        mVers.validate() == false
        mVers.errors.errorCount == 3

        when: "zero height"
        mVers.heightInPixels = 0

        then:
        mVers.validate() == false
        mVers.errors.errorCount == 3

        when: "restore to positive values"
        mVers.sizeInBytes = 888
        mVers.widthInPixels = 888
        mVers.heightInPixels = 888

        then:
        mVers.validate()
        mVers.errors.errorCount == 0
    }
}
