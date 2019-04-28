package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.action.*
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class TeamService implements ManagesDomain.Creater<Team>, ManagesDomain.Updater<Team>, ManagesDomain.Deleter {

    LocationService locationService
    PhoneService phoneService
    TeamActionService teamActionService

    @RollbackOnResultFailure
    Result<Team> tryCreate(Long orgId, TypeMap body) {
        Organizations.mustFindForId(orgId)
            .then { Organization org1 ->
                locationService.tryCreate(body.typeMapNoNull("location")).curry(org1)
            }
            .then { Organization org1, Location loc1 ->
                Team.tryCreate(org1, body.string("name"), loc1)
            }
            .then { Team t1 -> trySetFields(t1, body) }
            .then { Team t1 -> tryHandleTeamActions(t1, body) }
            .then { Team t1 ->
                String tzId = body.string("timezone")
                tryUpdatePhone(t1, body.long("staffId"), body.typeMapNoNull("phone"), tzId).curry(t1)
            }
            .then { Team t1 -> IOCUtils.resultFactory.success(t1, ResultStatus.CREATED) }
    }

    @RollbackOnResultFailure
    Result<Team> tryUpdate(Long tId, TypeMap body) {
        Teams.mustFindForId(tId)
            .then { Team t1 -> trySetFields(t1, body) }
            .then { Team t1  ->
                locationService.tryUpdate(t1.location, body.typeMapNoNull("location")).curry(t1)
            }
            .then { Team t1 -> tryHandleTeamActions(t1, body) }
            .then { Team t1 ->
                String tzId = body.string("timezone")
                tryUpdatePhone(t1, body.long("staffId"), body.typeMapNoNull("phone"), tzId).curry(t1)
            }
            .then { Team t1 -> IOCUtils.resultFactory.success(t1) }
    }

    @RollbackOnResultFailure
    Result<Void> tryDelete(Long tId) {
        Teams.mustFindForId(tId)
            .then { Team t1 ->
                t1.isDeleted = true
                DomainUtils.trySave(t1)
            }
    }

    // Helpers
    // -------

    protected Result<Team> trySetFields(Team t1, TypeMap body) {
        t1.with {
            if (body.name) name = body.name
            if (body.hexColor) hexColor = body.hexColor
        }
        DomainUtils.trySave(t1)
    }

    protected Result<Team> tryHandleTeamActions(Team t1, TypeMap body) {
        if (teamActionService.hasActions(body)) {
            teamActionService.tryHandleActions(t1, body)
        }
        else { IOCUtils.resultFactory.success(t1) }
    }

    protected Result<?> tryUpdatePhone(Team t1, Long staffId, TypeMap phoneInfo, String timezone) {
        // Only want to do logged-in check if the user is attempting to update this
        if (!phoneInfo) {
            return Result.void()
        }
        AuthUtils.tryGetAuthId()
            .then { Long authId -> Staffs.isAllowed(staffId ?: authId) }
            .then { Long sId ->
                phoneService.tryFindAnyIdOrCreateImmediatelyForOwner(t1.id, PhoneOwnershipType.GROUP)
                    .curry(sId)
            }
            .then { Long sId, Long pId -> Phones.mustFindForId(pId).curry(sId) }
            .then { Long sId, Phone p1 -> phoneService.tryUpdate(p1, phoneInfo, sId, timezone) }
    }
}
