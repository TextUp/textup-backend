package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class TeamService {

    LocationService locationService
    PhoneService phoneService
    TeamActionService teamActionService

    @RollbackOnResultFailure
    Result<Team> create(TypeMap body, String timezone) {
        Organizations.mustFindForId(body.long("org"))
            .then { Organization org1 ->
                locationService.create(body.typeMapNoNull("location")).curry(org1)
            }
            .then { Organization org1, Location loc1 ->
                Team.create(org1, body.string("name"), loc1)
            }
            .then { Team t1 -> trySetFields(t1, body) }
            .then { Team t1 -> teamActionService.tryHandleActions(t1, body) }
            .then { Team t1 -> tryUpdatePhone(t1, body.typeMapNoNull("phone"), timezone).curry(t1) }
            .then { Team t1 -> IOCUtils.resultFactory.success(t1, ResultStatus.CREATED) }
    }

    @RollbackOnResultFailure
    Result<Team> update(Long tId, TypeMap body, String timezone) {
        Teams.mustFindForId(tId)
            .then { Team t1 -> trySetFields(t1, body) }
            .then { Team t1  ->
                locationService.tryUpdate(t1.location, body.typeMapNoNull("location")).curry(t1)
            }
            .then { Team t1 -> teamActionService.tryHandleActions(t1, body) }
            .then { Team t1 -> tryUpdatePhone(t1, body.typeMapNoNull("phone"), timezone).curry(t1) }
            .then { Team t1 -> IOCUtils.resultFactory.success(t1) }
    }

    @RollbackOnResultFailure
    Result<Void> delete(Long tId) {
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

    protected Result<?> tryUpdatePhone(Team t1, TypeMap phoneInfo, String timezone) {
        // Only want to do admin check if the user is attempting to update this
        if (!phoneInfo) {
            return IOCUtils.resultFactory.success()
        }
        AuthUtils.tryGetAuthId()
            .then { Long authId -> Organizations.tryIfAdmin(t1.org.id, authId) }
            .then { Phones.mustFindForOwner(t1.id, PhoneOwnershipType.GROUP, true) }
            .then { Phone p1 -> phoneService.update(p1, phoneInfo, timezone) }
    }
}
