package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.type.*
import spock.lang.*

class MediaElementVersionSpec extends Specification {

    void "test constraints + width and link custom getters"() {
        given: "storage service mock"
        StorageService storageServiceMock = [
            generateAuthLink: { String key ->
                key ? new Result(success: true, payload: new URL("https://www.example.com")) :
                    new Result(success: false, payload: null) as Result<URL>
            }
        ] as StorageService

        when: "empty obj"
        MediaElementVersion mVers = new MediaElementVersion()
        mVers.storageService = storageServiceMock

        then: "invalid + custom getters are null-safe"
        mVers.validate() == false
        mVers.inherentWidth == null // null b/c no version set to fall back on
        mVers.link == null

        when: "filled in obj"
        mVers.mediaVersion = MediaVersion.SEND
        mVers.key = UUID.randomUUID().toString()
        mVers.sizeInBytes = 888

        then: "valid, width is not mandatory"
        mVers.validate() == true
        mVers.inherentWidth == MediaVersion.SEND.maxWidthInPixels
        mVers.link?.toString() == "https://www.example.com"

        when: "width is set"
        mVers.widthInPixels = 12345

        then: "user custom set width instead of fallback MediaVersion width"
        mVers.inherentWidth == 12345
    }

    void "test constraint errors"() {
        given: "a valid obj"
        MediaElementVersion mVers = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
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
