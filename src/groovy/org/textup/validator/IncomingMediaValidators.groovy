package org.textup.validator

import com.twilio.rest.api.v2010.account.message.Media
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.util.logging.Log4j
import org.springframework.validation.Errors
import org.textup.*
import org.textup.util.*

@GrailsTypeChecked
interface IsIncomingMedia {
    String getAccountId()
    String getMimeType()
    String getUrl()
    String getMediaId()
    boolean getIsPublic()
    Result<Boolean> delete()

    boolean validate()
    Errors getErrors()
}

@GrailsTypeChecked
@Validateable
@Log4j
class IncomingMediaInfo implements IsIncomingMedia {
    String accountId
    String mimeType
    String url
    String messageId
    String mediaId
    boolean isPublic = false

    static constraints = {
        url url: true
    }

    // [UNTESTED] because of limitations in mocking
    Result<Boolean> delete() {
        try {
            IOCUtils.resultFactory.success(Media.deleter(accountId, messageId, mediaId).delete())
        }
        catch (Throwable e) {
            log.error("SmsMediaDeleter.delete: ${e.message}")
            e.printStackTrace()
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }
}

@GrailsTypeChecked
@Validateable
@Log4j
class IncomingRecordingInfo implements IsIncomingMedia {
    String accountId
    String mimeType
    String url
    String mediaId
    boolean isPublic = false

    static constraints = {
        url url: true
    }

    // [UNTESTED] because of limitations in mocking
    Result<Boolean> delete() {
        try {
            IOCUtils.resultFactory.success(Recording.deleter(accountId, mediaId).delete())
        }
        catch (Throwable e) {
            log.error("CallRecordingDeleter.delete: ${e.message}")
            e.printStackTrace()
            IOCUtils.resultFactory.failWithThrowable(e)
        }
    }
}
