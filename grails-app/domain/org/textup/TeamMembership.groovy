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
        staffIdsOnSameTeamAs { thisStaffId ->
            projections { property("staff.id") }
            "in"("team.id", Team.teamIdsForStaffId(thisStaffId).list())
        }
        staffIdsForTeam { thisTeam ->
            projections { property("staff.id") }
            eq("team", thisTeam)
        }
        staffIdsForTeamId { thisTeamId ->
            projections { property("staff.id") }
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
