package org.textup

import grails.transaction.Transactional
import static org.springframework.http.HttpStatus.*

@Transactional
class TeamService {

	def resultFactory
    def authService

    Result<Team> create(Map body) {
        Long orgId = body?.org
        Organization o1 = Organization.get(orgId)
        if (o1) {
        	Team t1 = new Team()
            t1.with {
                name = body.name
                org = o1
                location = new Location(body.location)
            }

            if (!t1.location.save()) {
                return resultFactory.failWithValidationErrors(t1.location.errors)
            }
        	if (body.phone) {
        		TeamPhone p1 = new TeamPhone()
        		p1.numberAsString = body.phone
        		t1.phone = p1
                if (!p1.save()) {
                    return resultFactory.failWithValidationErrors(p1.errors)
                }
        	}
        	if (t1.save()) { resultFactory.success(t1) }
        	else { resultFactory.failWithValidationErrors(t1.errors) }
        }
        else {
            resultFactory.failWithMessageAndStatus(NOT_FOUND,
                "teamService.create.orgNotFound", [orgId])
        }
    }

    Result<Team> update(Long teamId, Map body) {
    	Team t1 = Team.get(teamId)
    	if (t1) {
            //Do these first so you don't have to call discard previous changes
            if (body.doTeamActions) {
                def teamActions = body.doTeamActions
                if (teamActions instanceof List) {
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
                                Result addRes = s1.addToTeam(t1)
                                if (!addRes.success) return addRes
                                break
                            case Constants.TEAM_ACTION_REMOVE:
                                s1.removeFromTeam(t1)
                                break
                            default:
                                return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                                    "teamService.update.teamActionInvalid",
                                    [tAction.action])
                        }
                    }
                }
                else {
                    return resultFactory.failWithMessageAndStatus(BAD_REQUEST,
                        "teamService.update.teamActionNotList")
                }
            }

            //Do other update operations
    		if (body.name) { t1.name = body.name }
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
	    	if (body.phone) {
	    		if (t1.phone) { t1.phone.numberAsString = body.phone }
	    		else {
	    			TeamPhone p1 = new TeamPhone()
		    		p1.numberAsString = body.phone
		    		t1.phone = p1
	    		}
                if (!t1.phone.save()) {
                    return resultFactory.failWithValidationErrors(t1.phone.errors)
                }
	    	}
	    	if (t1.save()) { resultFactory.success(t1) }
	    	else { resultFactory.failWithValidationErrors(t1.errors) }
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND,
    			"teamService.update.notFound", [teamId])
    	}
    }

    Result delete(Long teamId) {
    	Team t1 = Team.get(teamId)
    	if (t1) {
    		t1.delete()
    		resultFactory.success()
    	}
    	else {
    		resultFactory.failWithMessageAndStatus(NOT_FOUND,
    			"teamService.delete.notFound", [teamId])
    	}
    }
}
