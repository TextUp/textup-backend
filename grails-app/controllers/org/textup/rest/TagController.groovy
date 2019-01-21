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
class TagController extends BaseController {

    PhoneCache phoneCache
    TagService tagService

    @Transactional(readOnly=true)
    @Override
    void index() {
        ControllerUtils.tryGetPhoneId(body.long("teamId"))
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long pId ->
                respondWithCriteria(GroupPhoneRecords.buildForPhoneIdAndOptions(pId),
                    params,
                    GroupPhoneRecords.forSort(),
                    MarshallerUtils.KEY_TAG)
            }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        Long id = params.long("id")
        doShow({ PhoneRecords.isAllowed(id) }, { GroupPhoneRecords.mustFindForId(id) })
    }

    @Override
    void save() {
        doSave(MarshallerUtils.KEY_TAG, request, tagService) { TypeMap body ->
            ControllerUtils.tryGetPhoneId(body.long("teamId"))
        }
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_TAG, request, tagService) { TypeMap body ->
            PhoneRecords.isAllowed(params.long("id"))
        }
    }

    @Override
    void delete() {
        doDelete(tagService) { PhoneRecords.isAllowed(params.long("id")) }
    }
}
