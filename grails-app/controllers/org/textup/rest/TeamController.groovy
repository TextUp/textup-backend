package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.springframework.security.access.annotation.Secured
import org.springframework.transaction.annotation.Isolation
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

// [NOTE] Isolation level for this parent `Transaction` needs to be `READ_COMMITTED` instead of the
// MySQL default `REPEATABLE_READ` so that the new `Phone` created and then fetched via id will
// not return a not-found error. See: https://stackoverflow.com/a/18697026

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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    def save() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        doSave(MarshallerUtils.KEY_TEAM, request, teamService) { TypeMap body ->
            body.timezone = qParams.string("timezone")
            Organizations.isAllowed(body.long("org"))
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
