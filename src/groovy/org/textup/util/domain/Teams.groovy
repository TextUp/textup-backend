package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class Teams {

    @GrailsTypeChecked
    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    @GrailsTypeChecked
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
            .build(forStaffIds(staffIds))
            .build(forActive())
    }

    static DetachedCriteria<Team> buildActiveForOrgIdAndName(Long orgId, String name) {
        new DetachedCriteria(Team)
            .build {
                eq("org.id", orgId)
                eq("name", name)
            }
            .build(forActive())
    }

    // simulated INTERSECT, see http://www.mysqltutorial.org/mysql-intersect/
    static boolean hasTeamsInCommon(Long staffId1, Long staffId2) {
        new DetachedCriteria(Team)
            .build {
                "in"("id", Teams.buildForStaffIds([staffId1])
                    .build(CriteriaUtils.returnsId()))
            }
            .build(forStaffIds([staffId2]))
            .count() > 0
    }

    static boolean teamContainsMember(Long teamId, Long staffId) {
        new DetachedCriteria(Team)
            .build { idEq(teamId) }
            .build(forStaffIds([staffId]))
            .count() > 0
    }

    // Helpers
    // -------

    protected static Closure forActive() {
        return {
            eq("isDeleted", false)
        }
    }

    protected static Closure forStaffIds(Collection<Long> staffIds) {
        return {
            members { CriteriaUtils.inList(delegate, "id", staffIds) }
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
