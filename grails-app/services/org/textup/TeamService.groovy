package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.action.ActionContainer
import org.textup.validator.action.TeamAction
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@Transactional
class TeamService {

	ResultFactory resultFactory
    AuthService authService
    PhoneService phoneService

    // Create
    // ------

    @RollbackOnResultFailure
    Result<Team> create(Map body, String timezone) {
        Organization o1 = Organization.get(Helpers.to(Long, body.org))
        if (!o1) {
            return resultFactory.failWithCodeAndStatus("teamService.create.orgNotFound",
                ResultStatus.NOT_FOUND, [body.org])
        }
        updateTeamInfo(new Team(org:o1), body)
            .then({ Team t1 -> phoneService.mergePhone(t1, body, timezone) })
            .then({ Team t1 -> resultFactory.success(t1, ResultStatus.CREATED) })
    }
    protected Result<Team> updateTeamInfo(Team t1, Map body) {
        if (body.name) { t1.name = body.name }
        if (body.hexColor) { t1.hexColor = body.hexColor }
        if (body.location instanceof Map) {
            Map l = body.location as Map
            Location loc = t1.location ?: new Location()
            loc.with {
                if (l.address) address = l.address
                if (l.lat) lat = Helpers.to(BigDecimal, l.lat)
                if (l.lon) lon = Helpers.to(BigDecimal, l.lon)
            }
            t1.location = loc
            if (!loc.save()) {
                return resultFactory.failWithValidationErrors(loc.errors)
            }
        }
        if (t1.save()) {
            resultFactory.success(t1)
        }
        else { resultFactory.failWithValidationErrors(t1.errors) }
    }

    // Update
    // ------

    @RollbackOnResultFailure
    Result<Team> update(Long tId, Map body, String timezone) {
        findTeamFromId(tId)
            .then({ Team t1 -> handleTeamActions(t1, body) })
            .then({ Team t1 -> updateTeamInfo(t1, body) })
            .then({ Team t1 -> phoneService.mergePhone(t1, body, timezone) })
    }
    protected Result<Team> findTeamFromId(Long tId) {
        Team t1 = Team.get(tId)
        if (t1) {
            resultFactory.success(t1)
        }
        else {
            resultFactory.failWithCodeAndStatus("teamService.update.notFound",
                ResultStatus.NOT_FOUND, [tId])
        }
    }
    protected Result<Team> handleTeamActions(Team t1, Map body) {
        if (body.doTeamActions) {
            ActionContainer ac1 = new ActionContainer(body.doTeamActions)
            List<TeamAction> actions = ac1.validateAndBuildActions(TeamAction)
            if (ac1.hasErrors()) {
                return resultFactory.failWithValidationErrors(ac1.errors)
            }
            for (TeamAction a1 in actions) {
                Staff s1 = a1.staff
                if (!authService.hasPermissionsForStaff(s1.id)) {
                    return resultFactory.failWithCodeAndStatus("teamService.update.staffForbidden",
                        ResultStatus.FORBIDDEN, [s1.id])
                }
                switch (a1) {
                    case Constants.TEAM_ACTION_ADD:
                        t1.addToMembers(s1)
                        break
                    default: // Constants.TEAM_ACTION_REMOVE
                        t1.removeFromMembers(s1)
                }
            }
        }
        resultFactory.success(t1)
    }

    // Delete
    // ------

    @RollbackOnResultFailure
    Result<Void> delete(Long tId) {
    	Team t1 = Team.get(tId)
    	if (t1) {
    		t1.isDeleted = true
            if (t1.save()) {
                resultFactory.success()
            }
            else { resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		resultFactory.failWithCodeAndStatus("teamService.delete.notFound",
                ResultStatus.NOT_FOUND, [tId])
    	}
    }
}
