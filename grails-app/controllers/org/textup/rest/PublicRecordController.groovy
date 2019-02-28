package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured("permitAll")
@Transactional
class PublicRecordController extends BaseController {

    CallbackService callbackService
    CallbackStatusService callbackStatusService
    ThreadService threadService

    @Override
    def save() {
        TypeMap qParams = TypeMap.create(params)
        TwilioUtils.validate(request, qParams)
            .then {
                if (qParams[CallbackUtils.PARAM_HANDLE] == CallbackUtils.STATUS) {
                    threadService.delay(5, TimeUnit.SECONDS) {
                        callbackStatusService.process(qParams)
                    }
                    TwilioUtils.noResponseTwiml()
                }
                else { callbackService.process(qParams) }
            }
            .alwaysEnd { Result<?> res -> respondWithResult(res) }
    }
}
