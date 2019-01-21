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
class TeamController extends BaseController {

    TeamService teamService

    @Transactional(readOnly=true)
    @Override
    void index() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        AuthUtils.tryGetAuthId()
            .then { Long authId ->
                Long orgId = params.long("organizationId")
                Long staffId = params.long("staffId", authId)
                DetachedCriteria<Team> criteria = orgId ?
                    Teams.buildForOrgIds([orgId]) :
                    Teams.buildForStaffIds([staffId])
                respondWithCriteria(Staff, criteria, params)
            }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        Long id = params.long("id")
        Teams.isAllowed(id)
            .then { Teams.mustFindForId(id) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void save() {
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> Organizations.isAllowed(body.long("org")).curry(body) }
            .then { TypeMap body, Long orgId ->
                String tz = params.string("timezone")
                RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, tz)
                teamService.create(orgId, body, tz)
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void update() {
        Long id = params.long("id")
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> Teams.isAllowed(id).curry(body) }
            .then { TypeMap body ->
                String tz = params.string("timezone")
                RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, tz)
                teamService.update(id, body, tz)
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Override
    void delete() {
        Long id = params.long("id")
        Teams.isAllowed(id)
            .then { teamService.delete(id) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }
}
