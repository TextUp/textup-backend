package org.textup.validator

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import java.nio.charset.StandardCharsets

@TestMixin(GrailsUnitTestMixin)
class UploadItemSpec extends Specification {

    void "test constraints"() {
        when: "empty upload item"
        UploadItem uItem = new UploadItem()

        then:
        uItem.validate() == false
        uItem.errors.errorCount == 3

        when: "invalid mimeType"
        uItem.mimeType = "invalid MIME type"

        then:
        uItem.validate() == false
        uItem.errors.errorCount == 3

        when: "invalidly encoded data"
        uItem.mimeType = "image/jpeg"
        uItem.data = "invalidly encoded data!"

        then:
        uItem.validate() == false
        uItem.errors.errorCount == 2

        when: "data does not match checksum"
        uItem.data = Base64.encodeBase64String(uItem.data.getBytes(StandardCharsets.UTF_8))
        uItem.checksum = "invalid checksum"

        then:
        uItem.validate() == false
        uItem.errors.errorCount == 1
        uItem.errors.getFieldErrorCount("checksum") == 1

        when: "all valid"
        uItem.checksum = DigestUtils.md5Hex(uItem.data)

        then:
        uItem.validate() == true
    }

    void "test getting stream"() {
        when: "empty upload item"
        UploadItem uItem = new UploadItem()

        then:
        uItem.validate() == false
        uItem.stream == null

        when: "data is not base64 encoded"
        uItem.data = "invalidly encoded data!"

        then:
        uItem.validate() == false
        uItem.stream == null

        when: "when data is validly encoded"
        uItem.data = Base64.encodeBase64String(uItem.data.getBytes(StandardCharsets.UTF_8))

        then:
        uItem.validate() == false // can get stream as long as data is valid
        uItem.stream instanceof ByteArrayInputStream
    }
}