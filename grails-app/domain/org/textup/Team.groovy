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
class Team implements WithId {

    boolean isDeleted = false
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
	String name
	Location location
	Organization org
    String hexColor = Constants.DEFAULT_BRAND_COLOR

    static hasMany = [members: Staff]
    static mapping = {
        whenCreated type: PersistentDateTime
        members lazy: false, cascade: "save-update"
        location fetch: "join", cascade: "save-update"
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
        hexColor blank:false, nullable:false, validator:{ String val, Team obj ->
            //String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
        }
        location cascadeValidation: true
    }

    // Properties
    // ----------

    List<Staff> getActiveMembers() {
        (this.members?.findAll { Staff s1 ->
            s1.status == StaffStatus.STAFF || s1.status == StaffStatus.ADMIN
        } ?: []) as List<Staff>
    }

    Collection<Staff> getMembersByStatus(Collection statuses = []) {
        if (statuses) {
            HashSet<StaffStatus> findStatuses =
                new HashSet<>(TypeConversionUtils.toEnumList(StaffStatus, statuses))
            this.members.findAll { Staff s1 ->
                s1.status in findStatuses
            }
        }
        else { this.members }
    }
}
