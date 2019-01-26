package org.textup.validator

import com.twilio.rest.api.v2010.account.message.Media
import com.twilio.rest.api.v2010.account.Recording
import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.TupleConstructor
import groovy.util.logging.Log4j
import org.springframework.validation.Errors
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
@Log4j
@TupleConstructor(includeFields = true)
@Validateable
class IncomingMediaInfo implements IsIncomingMedia  {

    final String accountId
    final String mediaId
    final String messageId
    final String mimeType
    final String url

    boolean isPublic = false

    static constraints = {
        url url: true
    }

    static Result<IncomingMediaInfo> tryCreate(String messageId, TypeMap params, int index) {
        String contentUrl = params.string(TwilioUtils.MEDIA_URL_PREFIX + index)
        IncomingMediaInfo im1 = new IncomingMediaInfo(params.string(TwilioUtils.ID_ACCOUNT),
            TwilioUtils.extractMediaIdFromUrl(contentUrl),
            messageId,
            params.string(TwilioUtils.MEDIA_CONTENT_TYPE_PREFIX + index),
            contentUrl)
        DomainUtils.tryValidate(im1, ResultStatus.CREATED)
    }

    // Methods
    // -------

    // [UNTESTED] because of limitations in mocking
    @Override
    Result<Boolean> delete() {
        try {
            IOCUtils.resultFactory.success(Media.deleter(accountId, messageId, mediaId).delete())
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "delete", true)
        }
    }
}
