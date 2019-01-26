package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([CustomAccountDetails, MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class MediaElementVersionSpec extends Specification {

    void "test constraints + width and link custom getters"() {
        given: "storage service mock"
        MockedMethod unsignedLink = TestUtils.mock(LinkUtils, 'unsignedLink')
        MockedMethod signedLink = TestUtils.mock(LinkUtils, 'signedLink')

        when: "empty obj"
        MediaElementVersion mVers = new MediaElementVersion()

        then: "invalid + custom getters are null-safe"
        mVers.validate() == false
        mVers.link == null

        unsignedLink.callCount == 0
        signedLink.callCount == 1

        when: "filled in obj"
        mVers.type = MediaType.IMAGE_JPEG
        mVers.versionId = TestUtils.randString()
        mVers.sizeInBytes = 888
        mVers.link?.toString()

        then: "valid, defaults to being private"
        unsignedLink.callCount == 0
        signedLink.callCount == 2
        signedLink.callArguments[1][0] == mVers.versionId
        mVers.validate() == true

        when: "is public"
        mVers.isPublic = true
        mVers.link?.toString()

        then:
        unsignedLink.callCount == 1
        unsignedLink.callArguments[0][0] == mVers.versionId
        signedLink.callCount == 2
        mVers.validate() == true
    }

    void "test constraint errors"() {
        given: "a valid obj"
        MediaElementVersion mVers = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: TestUtils.randString(),
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
