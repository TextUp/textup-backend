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
        String search = params.string("search")
        respondWithCriteria(CLASS, Organizations.buildForOptions(search), params)
    }

    @Transactional(readOnly=true)
    @Secured(Roles.PUBLIC)
    @Override
    void show() {
        respondWithResult(CLASS, Organizations.mustFindForId(params.long("id")))
    }

    @Override
    void update() {
        Long orgId = params.long("id")
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> Organizations.isAllowed(orgId).curry(body) }
            .then { TypeMap body -> organizationService.update(orgId, body) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }
}
