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
@Transactional
class FutureMessageController extends BaseController {

	FutureMessageService futureMessageService

    @Override
    void index() {
        Long prId = params.long("contactId") ?: params.long("tagId")
        RequestUtils.trySetOnRequest(RequestUtils.PHONE_RECORD_ID, prId)
        PhoneRecords.isAllowed(prId)
            .then { Long prId -> PhoneRecords.mustFindForId(prId) }
            .then { PhoneRecord pr1 -> pr1.toWrapper().tryGetReadOnlyRecord() }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { ReadOnlyRecord rec1 ->
                respondWithCriteria(FutureMessages.buildForRecordIds([rec1.id]),
                    params,
                    null,
                    MarshallerUtils.KEY_FUTURE_MESSAGE)
            }
    }

    @Override
    void show() {
        Long id = params.long("id")
        doShow({ FutureMessages.isAllowed(id) }, { FutureMessages.mustFindForId(id) })
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_FUTURE_MESSAGE, request, futureMessageService) { TypeMap body ->
            Long prId = params.long("contactId") ?: params.long("tagId")
            RequestUtils.trySetOnRequest(RequestUtils.PHONE_RECORD_ID, prId)
            PhoneRecords.isAllowed(prId)
        }
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_FUTURE_MESSAGE, request, futureMessageService) { TypeMap body ->
            FutureMessages.isAllowed(params.long("id"))
        }
    }

    @Override
    void delete() {
        doDelete(futureMessageService) { PhoneRecords.isAllowed(params.long("id")) }
    }
}
