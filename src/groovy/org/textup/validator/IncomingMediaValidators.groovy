package org.textup

import com.twilio.rest.api.v2010.account.message.Media
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.util.logging.Log4j
import org.springframework.validation.Errors
import org.textup.*

@GrailsTypeChecked
interface IsIncomingMedia {
    String getMimeType()
    String getUrl()
    String getMediaId()
    boolean getIsPublic()
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
    boolean isPublic = false

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
    boolean isPublic = false

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
