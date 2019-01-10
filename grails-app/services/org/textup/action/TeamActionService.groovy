package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class TeamActionService implements HandlesActions<Team, Team> {

    @Override
    boolean hasActions(Map body) { !!body.doTeamActions }

    @Override
    Result<Team> tryHandleActions(Team t1, Map body) {
        // don't need to check each staff member because we may want to support inter-org teams
        // but the logged-in user performing these actions must be an admin at team's org
        AuthUtils.tryGetAuthId()
            .then { Long authId -> Organizations.tryIfAdmin(t1.org.id, authId) }
            .then { ActionContainer.tryProcess(TeamAction, body.doTeamActions) }
            .then { List<TeamAction> actions ->
                actions.each { TeamAction a1 ->
                    switch (a1) {
                        case TeamAction.ADD:
                            t1.addToMembers(a1.staff)
                            break
                        default: TeamAction.REMOVE
                            t1.removeFromMembers(a1.staff)
                    }
                }
                DomainUtils.trySave(t1)
            }
    }
}
