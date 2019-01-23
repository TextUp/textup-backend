package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.converters.JSON
import grails.transaction.Transactional
import javax.servlet.http.HttpServletRequest
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
class StaffController extends BaseController {

    StaffService staffService

    @Override
    void index() {
        TypeMap data = TypeMap.create(params)
        Collection<StaffStatus> statuses = data
            .enumList(StaffStatus, "status[]", StaffStatus.ACTIVE_STATUSES)
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, data.string("timezone"))
        if (data.organizationId) {
            listForOrg(statuses, data)
        }
        else if (data.teamId) {
            listForTeam(statuses, data)
        }
        else { listForShareStaff(data) }
    }

    @Override
    void show() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        Long id = params.long("id")
        doShow({ Staffs.isAllowed(id) }, { Staffs.mustFindForId(id) })
    }

    @Secured(Roles.PUBLIC)
    @Override
    void save() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        RequestUtils.tryGetJsonBody(req, MarshallerUtils.KEY_STAFF)
            .then { TypeMap body -> staffService.create(body) }
            .anyEnd { Result<?> res -> respondWithResult(res) }
    }

    @Override
    void update() {
        RequestUtils.trySetOnRequest(RequestUtils.TIMEZONE, params.string("timezone"))
        doUpdate(MarshallerUtils.KEY_STAFF, request, staffService) { TypeMap body ->
            Staffs.isAllowed(params.long("id"))
        }
    }

    // Helpers
    // -------

    protected void listForOrg(Collection<StaffStatus> statuses, TypeMap data) {
        Organizations.isAllowed(data.long("organizationId"))
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long orgId ->
                String search = data.string("search")
                respondWithCriteria(Staffs.buildForOrgIdAndOptions(orgId, search, statuses),
                    params,
                    null,
                    MarshallerUtils.KEY_STAFF)
            }
    }

    protected void listForTeam(Collection<StaffStatus> statuses, TypeMap data) {
        Teams.isAllowed(data.long("teamId"))
            .then { Long tId -> Teams.mustFindForId(tId) }
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Team t1 ->
                Collection<Staff> staffs = t1.getMembersByStatus(statuses)
                respondWithMany({ staffs.size() }, { staffs }, data, MarshallerUtils.KEY_STAFF)
            }
    }

    protected void listForShareStaff(TypeMap data) {
        Staffs.isAllowed(data.long("shareStaffId"))
            .ifFail { Result<?> failRes -> respondWithResult(failRes) }
            .then { Long sId ->
                HashSet<Staff> staffs = Staffs.findEveryForSharingId(sId)
                respondWithMany({ staffs.size() }, { staffs }, data, MarshallerUtils.KEY_STAFF)
            }
    }
}
