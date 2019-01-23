package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
@Transactional
class SessionController extends BaseController {

    SessionService sessionService

    @Override
    void index() {
        ControllerUtils.tryGetPhoneId(body.long("teamId"))
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long pId ->
                Boolean call = params.bool("subscribedToCall"),
                    text = params.bool("subscribedToText")
                respondWithCriteria(IncomingSessions.buildForPhoneIdWithOptions(pId, call, text),
                    params,
                    null,
                    MarshallerUtils.KEY_SESSION)
            }
    }

    @Override
    void show() {
        Long id = params.long("id")
        doShow({ IncomingSessions.isAllowed(id) }, { IncomingSessions.mustFindForId(id) })
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_SESSION, request, sessionService) { TypeMap body ->
            ControllerUtils.tryGetPhoneId(body.long("teamId"))
        }
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_SESSION, request, sessionService) { TypeMap body ->
            IncomingSessions.isAllowed(params.long("id"))
        }
    }
}
