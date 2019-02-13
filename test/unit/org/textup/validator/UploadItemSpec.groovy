package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import javax.imageio.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.joda.time.*
import org.springframework.validation.Errors
import org.textup.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class UploadItemSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test constraints"() {
        given:
        MediaType type = MediaType.values()[0]
        byte[] data = TestUtils.randString().bytes

        when: "empty upload item"
        Result res = UploadItem.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "invalidly encoded data"
        res = UploadItem.tryCreate(type, data)

        then: "valid, assume data is valid, even if it isn't a valid image"
        res.status == ResultStatus.CREATED
        res.payload.key != null
        res.payload.type == type
        res.payload.data == data
        res.payload.sizeInBytes == data.size()
        res.payload.widthInPixels == null
        res.payload.heightInPixels == null
    }

    void "test setting with BufferedImage"() {
        given:
        MediaType type = MediaType.values()[0]
        byte[] inputData1 = TestUtils.getJpegSampleData512()
        BufferedImage image1 = ImageUtils.tryGetImageFromData(inputData1)

        when:
        Result res = UploadItem.tryCreate(type, inputData1, image1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.data == inputData1
        res.payload.sizeInBytes == inputData1.size()
        res.payload.widthInPixels == image1.width
        res.payload.heightInPixels == image1.height
    }
}
