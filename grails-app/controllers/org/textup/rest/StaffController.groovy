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
@Secured(["ROLE_USER", "ROLE_ADMIN"])
@Transactional
class StaffController extends BaseController {

    StaffService staffService

    @Override
    void index() {
        TypeMap qParams = TypeMap.create(params)
        Collection<StaffStatus> statuses = qParams
            .enumList(StaffStatus, "status[]") ?: StaffStatus.ACTIVE_STATUSES
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        if (qParams.organizationId) {
            listForOrg(statuses, qParams)
        }
        else if (qParams.teamId) {
            listForTeam(statuses, qParams)
        }
        else { listForShareStaff(qParams) }
    }

    @Override
    void show() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        Long id = qParams.long("id")
        doShow({ Staffs.isAllowed(id) }, { Staffs.mustFindForId(id) })
    }

    @Secured("permitAll")
    @Override
    void save() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        RequestUtils.tryGetJsonBody(request, MarshallerUtils.KEY_STAFF)
            .then { TypeMap body -> staffService.tryCreate(body) }
            .alwaysEnd { Result<?> res -> respondWithResult(res) }
    }

    @Override
    void update() {
        TypeMap qParams = TypeMap.create(params)
        RequestUtils.trySet(RequestUtils.TIMEZONE, qParams.string("timezone"))
        doUpdate(MarshallerUtils.KEY_STAFF, request, staffService) {
            Staffs.isAllowed(qParams.long("id"))
        }
    }

    // Helpers
    // -------

    protected void listForOrg(Collection<StaffStatus> statuses, TypeMap qParams) {
        Organizations.isAllowed(qParams.long("organizationId"))
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Long orgId ->
                String search = qParams.string("search")
                respondWithCriteria(Staffs.buildForOrgIdAndOptions(orgId, search, statuses),
                    params,
                    null,
                    MarshallerUtils.KEY_STAFF)
            }
    }

    protected void listForTeam(Collection<StaffStatus> statuses, TypeMap qParams) {
        Teams.isAllowed(qParams.long("teamId"))
            .then { Long tId -> Teams.mustFindForId(tId) }
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .thenEnd { Team t1 ->
                Collection<Staff> staffs = t1.getMembersByStatus(statuses)
                respondWithClosures({ staffs.size() }, { staffs }, qParams, MarshallerUtils.KEY_STAFF)
            }
    }

    protected void listForShareStaff(TypeMap qParams) {
        Staffs.isAllowed(qParams.long("shareStaffId"))
            .ifFailAndPreserveError { Result<?> failRes -> respondWithResult(failRes) }
            .then { Long sId ->
                Collection<Staff> staffs = Staffs.findEveryForSharingId(sId)
                respondWithClosures({ staffs.size() }, { staffs }, qParams, MarshallerUtils.KEY_STAFF)
            }
    }
}
