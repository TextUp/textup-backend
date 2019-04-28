package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
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
class TagController extends BaseController {

    TagService tagService

    @Override
    def index() {
        ControllerUtils.tryGetPhoneId(params.long("teamId"))
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long pId ->
                respondWithCriteria(GroupPhoneRecords.buildForPhoneIdAndOptions(pId),
                    params,
                    GroupPhoneRecords.forSort(),
                    MarshallerUtils.KEY_TAG)
            }
    }

    @Override
    def show() {
        Long id = params.long("id")
        doShow({ PhoneRecords.isAllowed(id) }, { GroupPhoneRecords.mustFindForId(id) })
    }

    @Override
    def save() {
        doSave(MarshallerUtils.KEY_TAG, request, tagService) {
            ControllerUtils.tryGetPhoneId(params.long("teamId"))
        }
    }

    @Override
    def update() {
        doUpdate(MarshallerUtils.KEY_TAG, request, tagService) {
            PhoneRecords.isAllowed(params.long("id"))
        }
    }

    @Override
    def delete() {
        doDelete(tagService) { PhoneRecords.isAllowed(params.long("id")) }
    }
}
