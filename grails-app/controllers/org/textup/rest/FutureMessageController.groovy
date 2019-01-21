package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class FutureMessageController extends BaseController {

    static final Class CLASS = FutureMessage
	static String namespace = "v1"

	FutureMessageService futureMessageService

    @Override
    protected String getNamespaceAsString() { namespace }

	@Transactional(readOnly=true)
    @Override
    void index() {
        Long prId = params.long("contactId") ?: params.long("tagId")
        PhoneRecords.isAllowed(prId)
            .then {
                respondWithCriteria(CLASS,
                    FutureMessages.buildForPhoneRecordIds([prId]),
                    params,
                    false)
            }
            .ifFail { Result<?> failRes -> respondWithResult(CLASS, failRes) }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        Long gprId = params.long("id")
        FutureMessages.isAllowed(gprId)
            .then { FutureMessages.mustFindForId(gprId) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void save() {
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body ->
                Long prId = params.long("contactId") ?: params.long("tagId")
                PhoneRecords.isAllowed(prId).curry(body)
            }
            .then { TypeMap body, Long prId ->
                futureMessageService.create(prId, body, params.string("timezone"))
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void update() {
        Long fId = params.long("id")
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> FutureMessages.isAllowed(fId).curry(body) }
            .then { TypeMap body ->
                futureMessageService.update(fId, body, params.string("timezone"))
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void delete() {
        Long fId = params.long("id")
        FutureMessages.isAllowed(fId)
            .then { futureMessageService.delete(fId) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }
}
