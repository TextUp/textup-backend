package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class OrganizationController extends BaseController {

    OrganizationService organizationService

    @Transactional(readOnly=true)
    @Secured(Roles.PUBLIC)
    @Override
    void index() {
        respondWithCriteria(Organizations.buildForOptions(params.string("search")),
            params,
            null,
            MarshallerUtils.KEY_ORGANIZATION)
    }

    @Transactional(readOnly=true)
    @Secured(Roles.PUBLIC)
    @Override
    void show() {
        Long id = params.long("id")
        doShow({ Result.void() }, { IndividualPhoneRecordsWrappers.mustFindForId(id) })
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_ORGANIZATION, request, organizationService) { TypeMap body ->
            Organizations.isAllowed(params.long("id"))
        }
    }
}
