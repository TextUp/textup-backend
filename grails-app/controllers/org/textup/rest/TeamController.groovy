package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
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
class TeamController extends BaseController {

    TeamService teamService

    @Override
    def index() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        AuthUtils.tryGetAuthId()
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long authId ->
                Long orgId = qParams.long("organizationId")
                Long staffId = qParams.long("staffId", authId)
                DetachedCriteria<Team> criteria = orgId ?
                    Teams.buildActiveForOrgIds([orgId]) :
                    Teams.buildActiveForStaffIds([staffId])
                respondWithCriteria(criteria, params, null, MarshallerUtils.KEY_TEAM)
            }
    }

    @Override
    def show() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        Long id = qParams.long("id")
        doShow({ Teams.isAllowed(id) }, { Teams.mustFindForId(id) })
    }

    @Override
    def save() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        doSave(MarshallerUtils.KEY_TEAM, request, teamService) { TypeMap body ->
            body.timezone = qParams.string("timezone")
            Organizations.isAllowed(body.long("org"))
        }
    }

    @Override
    def update() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        doUpdate(MarshallerUtils.KEY_TEAM, request, teamService) { TypeMap body ->
            body.timezone = qParams.string("timezone")
            Teams.isAllowed(qParams.long("id"))
        }
    }

    @Override
    def delete() {
        doDelete(teamService) { Teams.isAllowed(params.long("id")) }
    }
}
