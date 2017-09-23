package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils

@GrailsCompileStatic
@EqualsAndHashCode
@ToString
@Validateable
class UploadItem {

    String mimeType
    String data
    String checksum

    UploadItem(Map params) {
        this.mimeType = params?.mimeType
        this.data = params?.data
        this.checksum = params?.checksum
    }

    static constraints = {
        mimeType nullable:false, blank:false, inList:["image/png", "image/jpeg"]
        data nullable:false, blank:false, validator:{ String data ->
            if (!Base64.isBase64(data)) {
                ["invalidFormat"]
            }
        }
        checksum nullable:false, blank:false, validator: { String checksum, UploadItem uItem ->
            if (checksum != DigestUtils.md5Hex(uItem.data)) {
                ["compromisedIntegrity"]
            }
        }
    }

    ByteArrayInputStream getStream() {
        if (data && Base64.isBase64(data)) {
            new ByteArrayInputStream(Base64.decodeBase64(this.data))
        }
    }
}