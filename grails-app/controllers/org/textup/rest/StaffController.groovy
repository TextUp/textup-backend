package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.HttpHeaders
import org.codehaus.groovy.grails.web.servlet.mvc.TypeMap
import org.springframework.security.access.annotation.Secured
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
@Secured(Roles.USER_ROLES)
class StaffController extends BaseController {

    StaffService staffService

    @Transactional(readOnly=true)
    @Override
    void index() {
        TypeMap data = TypeMap.create(params)
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, data.string("timezone"))
        Collection<StaffStatus> statuses = data.enumList(StaffStatus, "status[]",
            StaffStatus.ACTIVE_STATUSES)
        if (data.organizationId) {
            listForOrg(statuses, data)
        }
        else if (data.teamId) {
            listForTeam(statuses, data)
        }
        else { listForShareStaff(data) }
    }

    @Transactional(readOnly=true)
    @Override
    void show() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        Long id = params.long("id")
        Staffs.isAllowed(id)
            .then { Staffs.mustFindForId(id) }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    @Secured(Roles.PUBLIC)
    @Override
    void save() {
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body,
                String tz = params.string("timezone")
                RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, tz)
                respondWithResult(Staff, staffService.create(body, tz))
            }
    }

    @Override
    void update() {
        Long id = params.long("id")
        tryGetJsonPayload(CLASS, request)
            .then { TypeMap body -> Staffs.isAllowed(id).curry(body) }
            .then { TypeMap body ->
                String tz = params.string("timezone")
                RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, tz)
                staffService.update(id, body, tz)
            }
            .anyEnd { Result<?> res -> respondWithResult(CLASS, res) }
    }

    // Helpers
    // -------

    protected void listForOrg(Collection<StaffStatus> statuses, TypeMap data) {
        Organizations.isAllowed(data.long("organizationId"))
            .then { Long orgId ->
                respondWithCriteria(CLASS,
                    Staffs.buildForOrgIdAndOptions(orgId, data.string("search"), statuses),
                    params)
            }
    }

    protected void listForTeam(Collection<StaffStatus> statuses, TypeMap data) {
        Teams.isAllowed(data.long("teamId"))
            .then { Long tId -> Teams.mustFindForId(tId) }
            .then { Team t1 ->
                Collection<Staff> staffs = t1.getMembersByStatus(statuses)
                respondWithMany(Staff,
                    { staffs.size() },
                    { staffs },
                    data)
            }
    }

    protected void listForShareStaff(TypeMap data) {
        Staffs.isAllowed(data.long("shareStaffId"))
            .then { Long sId ->
                HashSet<Staff> staffs = Staffs.findEveryForSharingId(sId)
                respondWithMany(Staff,
                    { staffs.size() },
                    { staffs },
                    data)
            }
    }
}
