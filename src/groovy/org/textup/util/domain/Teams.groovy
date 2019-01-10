package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Teams {

    // TODO hasPermissionsForTeam
    static Result<Void> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId().then { Long authId ->
            AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0)
        }
    }

    static Result<Team> mustFindForId(Long teamId) {
        Team t1 = teamId ? Team.get(teamId) : null
        if (t1) {
            IOCUtils.resultFactory.success(t1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("teamService.update.notFound", // TODO
                ResultStatus.NOT_FOUND, [teamId])
        }
    }

    static DetachedCriteria<Team> buildForOrgIds(Collection<Long> orgIds) {
        new DetachedCriteria(Team)
            .build { CriteriaUtils.inList(delegate, "org.id", orgIds) }
            .build(forActive())
    }

    static DetachedCriteria<Team> buildForStaffIds(Collection<Long> staffIds) {
        new DetachedCriteria(Team)
            .build { members { CriteriaUtils.inList(delegate, "id", staffIds) } }
            .build(forActive())
    }

    // simulated INTERSECT, see http://www.mysqltutorial.org/mysql-intersect/
    static boolean hasTeamsInCommon(Long staffId1, Long staffId2) {
        new DetachedCriteria(Team)
            .build {
                "in"("id", Teams.buildForStaffIds([staffId1])
                    .build(CriteriaUtils.returnsId()))
            }
            .build(Teams.buildForStaffIds([staffId2]))
            .count() > 0
    }

    static boolean teamContainsMember(Long teamId, Long staffId) {
        new DetachedCriteria(Team)
            .build { idEq(teamId) }
            .build(Teams.buildForStaffIds([staffId]))
            .count() > 0
    }

    // Helpers
    // -------

    protected static Closure forActive() {
        return {
            eq("isDeleted", false)
        }
    }

    // Can have permission for this team if
    // (1) You are on this team
    // (2) You are an admin at this team's organization
    protected static DetachedCriteria<Team> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(Team).build {
            idEq(thisId)
            or {
                "in"("id", Teams.buildForStaffIds([authId]).build(CriteriaUtils.returnsId()))
                "in"("org", Organizations.buildActiveForAdminIds([authId]))
            }
        }
    }
}
