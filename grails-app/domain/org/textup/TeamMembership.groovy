package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode

@EqualsAndHashCode
class TeamMembership {

	Staff staff
	Team team

    static constraints = {
        staff validator: { val, obj ->
            if (val.org != obj.team?.org) {
                return ["differentOrgs", val.org?.name, obj.team?.org?.name]
            }
            if (obj.isDuplicate(val, obj.team)) { return ["duplicate"] }
        }
    }
    static namedQueries = {
        staffIdsOnSameTeamAs { Long thisStaffId ->
            projections { distinct("staff.id") }
            def res = Team.teamIdsForStaffId(thisStaffId).list()
            if (res) { "in"("team.id", res) }
            else { eq("team.id", null) }
        }
        staffOnSameTeamAs { Long thisStaffId ->
            projections { distinct("staff") }
            def res = Team.teamIdsForStaffId(thisStaffId).list()
            if (res) { "in"("team.id", res) }
            else { eq("team.id", null) }
        }
        staffIdsForTeam { Team thisTeam ->
            projections { distinct("staff.id") }
            eq("team", thisTeam)
        }
        staffIdsForTeamId { Long thisTeamId ->
            projections { distinct("staff.id") }
            team { eq("id", thisTeamId) }
        }
    }

    /*
	Has many:
	*/

    ////////////////////
    // Helper methods //
    ////////////////////

    private boolean isDuplicate(Staff s, Team t) {
        if ([s, t].any { it == null }) return false
        boolean hasDuplicate = false
        TeamMembership.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                TeamMembership m = TeamMembership.findByStaffAndTeam(s, t)
                if (m && m.id != this.id) hasDuplicate = true
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        hasDuplicate
    }

    /////////////////////
    // Property Access //
    /////////////////////

}
