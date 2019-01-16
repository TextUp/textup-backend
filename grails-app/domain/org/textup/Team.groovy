package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
class Team implements WithId, Saveable<Team> {

    boolean isDeleted = false
    DateTime whenCreated = DateTimeUtils.now()
    Location location
    Organization org
    String hexColor = Constants.DEFAULT_BRAND_COLOR
    String name

    static hasMany = [members: Staff]
    static mapping = {
        cache usage: "read-write", include: "non-lazy"
        whenCreated type: PersistentDateTime
        members fetch: "join", cascade: "save-update" // TODO should be lazy for cache?
        location fetch: "join", cascade: "save-update" // TODO should be lazy for cache?
    }
    static constraints = {
    	name blank:false, validator: { String val, Team obj ->
            Closure<Boolean> hasExistingTeamName = {
                // uniqueness check should ignore deleted teams
                Team t1 = Team.findByOrgAndNameAndIsDeleted(obj.org, val, false)
                // HAS DUPLICATE IF (1) there is a team `t1` that belonging to this organization
                // that has name and is not deleted, and (2) this team `t1` is NOT the same as
                // the team we are validating
                t1 != null && t1.id != obj.id
            }
            //within an Org, team name must be unique
            if (val && Utils.<Boolean>doWithoutFlush(hasExistingTeamName)) {
                ["duplicate", obj.org?.name]
            }
        }
        hexColor blank:false, nullable:false, validator:{ String val ->
            if (!ValidationUtils.isValidHexCode(val)) { ["invalidHex"] }
        }
        location cascadeValidation: true
    }

    static Result<Team> tryCreate(Organization org1, String name, Location loc1) {
        DomainUtils.trySave(new Team(org: org1, name: name, location: loc1, ResultStatus.CREATED)
    }

    // Properties
    // ----------

    Collection<Staff> getActiveMembers() {
        members?.findAll { Staff s1 -> s1.status.isActive() } ?: new ArrayList<Staff>()
    }

    // Can't move to static class because Grails manages this relationship so no direct queries
    Collection<Staff> getMembersByStatus(Collection<StaffStatus> statuses) {
        if (statuses) {
            HashSet<StaffStatus> statusesToFind = new HashSet<>(statuses)
            members?.findAll { Staff s1 -> statusesToFind.contains(s1.status) }
        }
        else { members }
    }
}
