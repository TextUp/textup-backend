package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*
import grails.compiler.GrailsTypeChecked
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@Transactional
class TeamService {

	ResultFactory resultFactory
    AuthService authService
    PhoneService phoneService

    // Create
    // ------

    Result<Team> create(Map body) {
        Organization o1 = Organization.get(Helpers.toLong(body.org))
        if (!o1) {
            return resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "teamService.create.orgNotFound", [body.org])
        }
    	Team t1 = new Team(org:o1)
        Result.<Team>waterfall(
            this.&updateTeamInfo.curry(t1, body),
            phoneService.&createOrUpdatePhone.rcurry(body)
        ).then({
            if (t1.save()) {
                resultFactory.success(t1)
            }
            else { resultFactory.failWithValidationErrors(t1.errors) }
        }) as Result<Team>
    }
    protected Result<Team> updateTeamInfo(Team t1, Map body) {
        if (body.name) { t1.name = body.name }
        if (body.hexColor) { t1.hexColor = body.hexColor }
        if (body.location instanceof Map) {
            Map l = body.location as Map
            Location loc = t1.location ?: new Location()
            loc.with {
                if (l.address) address = l.address
                if (l.lat) lat = Helpers.toBigDecimal(l.lat)
                if (l.lon) lon = Helpers.toBigDecimal(l.lon)
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

    Result<Team> update(Long tId, Map body) {
        Result.<Team>waterfall(
            this.&findTeamFromId.curry(tId),
            this.&handleTeamActions.rcurry(body),
            this.&updateTeamInfo.rcurry(body),
            phoneService.&createOrUpdatePhone.rcurry(body)
        ).then({ Team t1 ->
            if (t1.save()) {
                resultFactory.success(t1)
            }
            else { resultFactory.failWithValidationErrors(t1.errors) }
        }) as Result
    }
    protected Result<Team> findTeamFromId(Long tId) {
        Team t1 = Team.get(tId)
        if (t1) {
            resultFactory.success(t1)
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "teamService.update.notFound", [tId])
        }
    }
    protected Result<Team> handleTeamActions(Team t1, Map body) {
        if (!body.doTeamActions) {
            return resultFactory.success(t1)
        }
        else if (!(body.doTeamActions instanceof Collection)) {
            return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                "teamService.update.teamActionNotList")
        }
        for (item in body.doTeamActions) {
            if (!(item instanceof Map)) { continue }
            Map tAction = item as Map
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
            switch(Helpers.toLowerCaseString(tAction.action)) {
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

    // Delete
    // ------

    Result delete(Long tId) {
    	Team t1 = Team.get(tId)
    	if (t1) {
    		t1.isDeleted = true
            if (t1.save()) {
                resultFactory.success()
            }
            else { resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND,
    			"teamService.delete.notFound", [tId])
    	}
    }
}
