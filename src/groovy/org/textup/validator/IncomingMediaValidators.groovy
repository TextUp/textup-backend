package org.textup

import grails.compiler.GrailsTypeChecked
import org.textup.*
import grails.validation.Validateable
import groovy.util.logging.Log4j

@GrailsTypeChecked
interface IsIncomingMedia {
    String getMimeType()
    String getUrl()
    String mediaId()
    Result<Boolean> delete()

    boolean validate()
    Errors getErrors()
}

// [UNTESTED] because of limitations in mocking
@GrailsTypeChecked
@Validateable
@Log4j
class IncomingMediaInfo implements IsIncomingMedia {
    String mimeType
    String url
    String messageId
    String mediaId

    Result<Boolean> delete() {
        try {
            Helpers.resultFactory.success(Media.deleter(messageId, mediaId).delete())
        }
        catch (Throwable e) {
            log.error("SmsMediaDeleter.delete: ${e.message}")
            e.printStackTrace()
            Helpers.resultFactory.failWithThrowable(e)
        }
    }
}

// [UNTESTED] because of limitations in mocking
@GrailsTypeChecked
@Validateable
@Log4j
class IncomingRecordingInfo implements IsIncomingMedia {
    String mimeType
    String url
    String mediaId

    Result<Boolean> delete() {
        try {
            Helpers.resultFactory.success(Recording.deleter(mediaId).delete())
        }
        catch (Throwable e) {
            log.error("CallRecordingDeleter.delete: ${e.message}")
            e.printStackTrace()
            Helpers.resultFactory.failWithThrowable(e)
        }
    }
}
