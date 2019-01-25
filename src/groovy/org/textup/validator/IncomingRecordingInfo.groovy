package org.textup.validator

import com.twilio.rest.api.v2010.account.message.Media
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.util.logging.Log4j
import org.springframework.validation.Errors
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
@Log4j
@Validateable
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
            IOCUtils.resultFactory.failWithThrowable(e, "delete", true)
        }
    }
}
