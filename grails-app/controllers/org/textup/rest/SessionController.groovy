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
class SessionController extends BaseController {

    SessionService sessionService

    @Transactional(readOnly=true)
    @Override
    void index() {
        ControllerUtils.tryGetPhoneOwner(body.long("teamId"))
            .then { Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    phoneCache.mustFindPhoneIdForOwner(ownerId, type)
                }
            }
            .ifFail { Result<?> failRes -> respondWithResult(CLASS, failRes) }
            .then { Long pId ->
                Boolean subToCall = params.bool("subscribedToCall"),
                    subToText = params.bool("subscribedToText")
                respondWithCriteria(IncomingSession,
                    IncomingSessions.buildForPhoneIdWithOptions(pId, subToCall, subToText)
                    params)
            }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        Long id = params.long("id")
        IncomingSessions.isAllowed(id)
            .then { IncomingSessions.mustFindForId(id) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void save() {
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body ->
                ControllerUtils.tryGetPhoneOwner(body.long("teamId")).curry(body)
            }
            .then { TypeMap body, Tuple<Long, PhoneOwnershipType> processed ->
                Tuple.split(processed) { Long ownerId, PhoneOwnershipType type ->
                    sessionService.create(ownerId, type, body)
                }
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void update() {
        Long id = params.long("id")
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> IncomingSessions.isAllowed(id).curry(body) }
            .then { TypeMap body -> sessionService.update(id, body) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }
}
