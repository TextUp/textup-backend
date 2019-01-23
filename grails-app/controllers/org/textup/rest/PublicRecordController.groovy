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
@Secured(Roles.PUBLIC)
@Transactional
class PublicRecordController extends BaseController {

    CallbackService callbackService
    CallbackStatusService callbackStatusService
    ThreadService threadService

    @Override
    def save() {
        TypeMap data = TypeMap.create(params)
        TwilioUtils.validate(request, data)
            .then {
                if (data[CallbackUtils.PARAM_HANDLE] == CallbackUtils.STATUS) {
                    threadService.delay(5, TimeUnit.SECONDS) {
                        callbackStatusService.process(data)
                    }
                    TwilioUtils.noResponseTwiml()
                }
                else { callbackService.process(data) }
            }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }
}
