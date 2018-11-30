package org.textup.validator

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import javax.imageio.*
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.springframework.validation.Errors
import org.textup.*
import org.textup.media.ImageUtils
import org.textup.type.*
import spock.lang.*

@Domain([MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class UploadItemSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "test constraints"() {
        when: "empty upload item"
        UploadItem uItem = new UploadItem()

        then:
        uItem.validate() == false
        uItem.errors.errorCount == 2 // 2 props = type, data

        when: "invalidly encoded data"
        uItem.type = MediaType.IMAGE_JPEG
        uItem.data = "not actually an image".bytes

        then: "valid, assume data is valid, even if it isn't a valid image"
        uItem.validate() == true
        uItem.widthInPixels == null // not actually an image so can't process into image to find width
        uItem.heightInPixels == null // not actually an image so can't process into image to find width
    }

    void "test getting size in bytes"() {
        when: "obj with valid mime type"
        byte[] inputData1 = TestUtils.getJpegSampleData512()
        UploadItem uItem = new UploadItem(type: MediaType.IMAGE_JPEG, data: inputData1)
        assert uItem.validate()

        then: "can get MediaType enum"
        uItem.type == MediaType.IMAGE_JPEG
        uItem.widthInPixels == null
        uItem.heightInPixels == null
        uItem.sizeInBytes == inputData1.size()

        when: "setting data"
        byte[] inputData2 = TestUtils.getJpegSampleData256()
        assert inputData1.size() != inputData2.size()
        uItem.data = inputData2

        then: "file size is implicit from data but dimensions need to be manually set"
        uItem.sizeInBytes == inputData2.size()
        uItem.widthInPixels == null
        uItem.heightInPixels == null
    }

    void "test setting properties via BufferedImage"() {
        given:
        byte[] inputData1 = TestUtils.getJpegSampleData512()
        byte[] inputData2 = TestUtils.getGifSampleData()
        BufferedImage image1 = ImageUtils.tryGetImageFromData(inputData1)
        BufferedImage image2 = ImageUtils.tryGetImageFromData(inputData2)

        UploadItem uItem = new UploadItem(type: MediaType.IMAGE_JPEG, data: inputData1)
        assert uItem.validate()

        when:
        uItem.image = image1

        then:
        uItem.heightInPixels != null
        uItem.widthInPixels != null
        uItem.heightInPixels == image1.height
        uItem.widthInPixels == image1.width

        when:
        uItem.image = image2

        then:
        uItem.heightInPixels != null
        uItem.widthInPixels != null
        uItem.heightInPixels == image2.height
        uItem.widthInPixels == image2.width
    }

    void "test converting to MediaElementVersion"() {
        given:
        byte[] inputData1 = TestUtils.getJpegSampleData512()
        UploadItem uItem = new UploadItem(type: MediaType.IMAGE_JPEG,
            data: inputData1, isPublic: true, widthInPixels: 888, heightInPixels: 888)
        assert uItem.validate()

        when:
        MediaElementVersion mVers1 = uItem.toMediaElementVersion()

        then:
        mVers1.validate()
        mVers1.type == uItem.type
        mVers1.versionId == uItem.key
        mVers1.sizeInBytes == inputData1.length
        mVers1.widthInPixels == uItem.widthInPixels
        mVers1.heightInPixels == uItem.heightInPixels
        mVers1.isPublic == uItem.isPublic
    }
}
