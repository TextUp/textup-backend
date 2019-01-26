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
class OrganizationController extends BaseController {

    OrganizationService organizationService

    @Secured("permitAll")
    @Override
    void index() {
        TypeMap qParams = TypeMap.create(params)
        respondWithCriteria(Organizations.buildForOptions(qParams.string("search")),
            qParams,
            null,
            MarshallerUtils.KEY_ORGANIZATION)
    }

    @Secured("permitAll")
    @Override
    void show() {
        Long id = params.long("id")
        doShow({ Result.void() }, { IndividualPhoneRecordWrappers.mustFindForId(id) })
    }

    @Override
    void update() {
        doUpdate(MarshallerUtils.KEY_ORGANIZATION, request, organizationService) {
            Organizations.isAllowed(params.long("id"))
        }
    }
}
