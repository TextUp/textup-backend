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

    final String accountId
    final String mediaId
    final String mimeType
    final String url

    boolean isPublic = false

    static constraints = {
        url url: true
    }

    static Result<IncomingRecordingInfo> tryCreate(TypeMap params) {
        IncomingRecordingInfo ir1 = new IncomingRecordingInfo(params.string(TwilioUtils.ID_ACCOUNT),
            params.string(TwilioUtils.ID_RECORDING),
            MediaType.AUDIO_MP3.mimeType,
            params.string(TwilioUtils.RECORDING_URL))
        DomainUtils.tryValidate(ir1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    // [UNTESTED] because of limitations in mocking
    @Override
    Result<Boolean> delete() {
        try {
            IOCUtils.resultFactory.success(Recording.deleter(accountId, mediaId).delete())
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "delete", true)
        }
    }
}
