package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Secured(["ROLE_USER", "ROLE_ADMIN"])
@Transactional
class FutureMessageController extends BaseController {

	FutureMessageService futureMessageService

    @Override
    def index() {
        Long prId = params.long("contactId") ?: params.long("tagId")
        RequestUtils.trySet(RequestUtils.PHONE_RECORD_ID, prId)
        PhoneRecords.isAllowed(prId)
            .then { PhoneRecordWrappers.mustFindForId(prId) }
            .then { PhoneRecordWrapper w1 -> w1.tryGetReadOnlyRecord() }
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { ReadOnlyRecord rec1 ->
                respondWithCriteria(FutureMessages.buildForRecordIds([rec1.id]),
                    params,
                    null,
                    MarshallerUtils.KEY_FUTURE_MESSAGE)
            }
    }

    @Override
    def show() {
        Long id = params.long("id")
        doShow({ FutureMessages.isAllowed(id) }, { FutureMessages.mustFindForId(id) })
    }

    @Override
    def save() {
        doSave(MarshallerUtils.KEY_FUTURE_MESSAGE, request, futureMessageService) {
            Long prId = params.long("contactId") ?: params.long("tagId")
            RequestUtils.trySet(RequestUtils.PHONE_RECORD_ID, prId)
            PhoneRecords.isAllowed(prId)
        }
    }

    @Override
    def update() {
        doUpdate(MarshallerUtils.KEY_FUTURE_MESSAGE, request, futureMessageService) {
            FutureMessages.isAllowed(params.long("id"))
        }
    }

    @Override
    def delete() {
        doDelete(futureMessageService) { FutureMessages.isAllowed(params.long("id")) }
    }
}
