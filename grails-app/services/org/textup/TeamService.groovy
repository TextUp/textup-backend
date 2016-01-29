package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*

@Transactional
class TeamService {

	def resultFactory
    def authService

    // Create
    // ------

    Result<Team> create(Map body) {
        Organization o1 = Organization.get(body.org)
        if (!o1) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "teamService.create.orgNotFound", [orgId])
        }
    	Team t1 = new Team()
        t1.with {
            name = body.name
            org = o1
            location = new Location(body.location)
            if (body.hexColor) hexColor = body.hexColor
        }
        if (!t1.location.save()) {
            return resultFactory.failWithValidationErrors(t1.location.errors)
        }
    	if (body.phone) {
    		Phone p1 = new Phone()
    		p1.numberAsString = body.phone
    		t1.phone = p1
            if (!p1.save()) {
                return resultFactory.failWithValidationErrors(p1.errors)
            }
    	}
    	if (t1.save()) {
            resultFactory.success(t1)
        }
    	else { resultFactory.failWithValidationErrors(t1.errors) }
    }

    // Update
    // ------

    Result<Team> update(Long teamId, Map body) {
        Result.<Team>waterfall(
            this.&findTeamFromId.curry(teamId),
            this.&handleTeamActions.rcurry(body),
            this.&updateTeam.rcurry(body)
        ).then({ Team t1 ->
            if (t1.save()) {
                resultFactory.success(t1)
            }
            else { resultFactory.failWithValidationErrors(t1.errors) }
        })
    }
    protected Result<Team> findTeamFromId(Long tId) {
        Team t1 = Team.get(teamId)
        if (t1) {
            resultFactory.success(t1)
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "teamService.update.notFound", [teamId])
        }
    }
    protected Result<Team> handleTeamActions(Team t1, Map body) {
        if (body.doTeamActions) { return }
        else if (body.doTeamActions instanceof List) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "teamService.update.teamActionNotList")
        }
        for (tAction in teamActions) {
            Staff s1 = Staff.get(Helpers.toLong(tAction.id))
            if (!s1) {
                return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                    "teamService.update.staffNotFound",
                    [tAction.action, tAction.id])
            }
            else if (!authService.hasPermissionsForStaff(s1.id)) {
                return resultFactory.failWithMessageAndStatus(FORBIDDEN,
                    "teamService.update.staffForbidden",
                    [tAction.id])
            }
            switch(tAction.action) {
                case Constants.TEAM_ACTION_ADD:
                    t1.addToMembers(s1)
                    break
                case Constants.TEAM_ACTION_REMOVE:
                    t1.removeFromMembers(s1)
                    break
                default:
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "teamService.update.teamActionInvalid",
                        [tAction.action])
            }
        }
        resultFactory.success(t1)
    }
    protected Result<Team> updateTeam(Team t1, Map body) {
        if (body.name) { t1.name = body.name }
        if (body.hexColor) { t1.hexColor = body.hexColor }
        if (body.location) {
            def l = body.location
            t1.location.with {
                if (l.address) address = l.address
                if (l.lat) lat = l.lat
                if (l.lon) lon = l.lon
            }
            if (!t1.location.save()) {
                return resultFactory.failWithValidationErrors(t1.location.errors)
            }
        }
        if (t1.phone && body.awayMessage) {
            t1.phone.awayMessage = body.awayMessage
            if (!t1.phone.save()) {
                return resultFactory.failWithValidationErrors(t1.phone.errors)
            }
        }
        if (body.phone) {
            if (t1.phone) {
                t1.phone.numberAsString = body.phone
            }
            else {
                Phone p1 = new Phone()
                p1.numberAsString = body.phone
                t1.phone = p1
            }
            if (!t1.phone.save()) {
                return resultFactory.failWithValidationErrors(t1.phone.errors)
            }
        }
        resultFactory.success(t1)
    }

    // Delete
    // ------

    Result delete(Long tId) {
    	Team t1 = Team.get(tId)
    	if (t1) {
    		t1.delete()
    		resultFactory.success()
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND,
    			"teamService.delete.notFound", [tId])
    	}
    }
}
