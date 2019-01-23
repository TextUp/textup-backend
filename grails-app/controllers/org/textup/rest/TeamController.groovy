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
@Secured(Roles.USER_ROLES)
@Transactional
class TeamController extends BaseController {

    TeamService teamService

    @Override
    void index() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        AuthUtils.tryGetAuthId()
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long authId ->
                Long orgId = params.long("organizationId")
                Long staffId = params.long("staffId", authId)
                DetachedCriteria<Team> criteria = orgId ?
                    Teams.buildForOrgIds([orgId]) :
                    Teams.buildForStaffIds([staffId])
                respondWithCriteria(criteria, params, null, MarshallerUtils.KEY_TEAM)
            }
    }

    @Override
    void show() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        Long id = params.long("id")
        doShow({ Teams.isAllowed(id) }, { Teams.mustFindForId(id) })
    }

    @Override
    void save() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        doSave(MarshallerUtils.KEY_TEAM, request, teamService) { TypeMap body ->
            Organizations.isAllowed(body.long("org"))
        }
    }

    @Override
    void update() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        doUpdate(MarshallerUtils.KEY_TEAM, request, teamService) { TypeMap body ->
            Teams.isAllowed(params.long("id"))
        }
    }

    @Override
    void delete() {
        doDelete(teamService) { Teams.isAllowed(params.long("id")) }
    }
}
