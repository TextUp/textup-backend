package org.textup

import grails.compiler.GrailsCompileStatic
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.hibernate.Session
import org.restapidoc.annotation.*
import org.textup.types.PhoneOwnershipType
import org.textup.types.StaffStatus

@EqualsAndHashCode
@RestApiObject(name="Team", description="A team at an organization.")
class Team {

    boolean isDeleted = false

    @RestApiObjectField(description="Name of the team")
	String name

    @RestApiObjectField(description="Location this team is based at. \
        Default same as organization.")
	Location location

    @RestApiObjectField(
        description = "Id of the organization this team belongs to",
        allowedType = "Number")
	Organization org

    @RestApiObjectField(
        description  = "Hex color code for this team",
        defaultValue = "#1BA5E0",
        mandatory    = false)
    String hexColor = "#1BA5E0"

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName      = "doTeamActions",
            description       = "List of some actions to perform on staff with \
                relation to this team",
            allowedType       = "List<[teamAction]>",
            useForCreation    = false,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName  = "phone",
            description   = "TextUp phone",
            allowedType   = "Phone"),
        @RestApiObjectField(
            apiFieldName = "hasInactivePhone",
            description  = "Whether this staff has an inactive TextUp phone",
            allowedType  = "Boolean",
            mandatory    = false)
    ])
    static transients = ["phone"]
    static hasMany = [members:Staff]
    static mapping = {
        members lazy:false, cascade:"save-update"
    }
    static constraints = {
    	name blank:false, validator: { String val, Team obj ->
            //within an Org, team name must be unique
            if (obj.hasExistingTeamName(val)) {
                ["duplicate", obj.org?.name]
            }
        }
        hexColor blank:false, nullable:false, validator:{ String val, Team obj ->
            //String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
        }
    }
    static namedQueries = {
        forStaffs { Collection<Staff> staffs ->
            eq("isDeleted", false)
            members {
                if (staffs) {
                    "in"("id", staffs*.id)
                }
                else { eq("id", null) }
            }
        }
    }

    // Static finders
    // --------------

    static List<Team> listForStaffs(Collection<Staff> staffs, Map params=[:]) {
        forStaffs(staffs).list(params)
    }

    // Validator
    // ---------

    @GrailsCompileStatic
    protected boolean hasExistingTeamName(String teamName) {
        boolean duplicateTeam = false
        Team.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                // uniqueness check should ignore deleted teams
                Team t = Team.findByOrgAndNameAndIsDeleted(this.org, teamName, false)
                if (t && t.id != this.id) { duplicateTeam = true }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        duplicateTeam
    }

    // Members
    // -------

    @GrailsCompileStatic
    Collection<Staff> getActiveMembers() {
        this.members?.findAll { Staff s1 ->
            s1.status == StaffStatus.STAFF || s1.status == StaffStatus.ADMIN
        } ?: []
    }
    @GrailsCompileStatic
    Collection<Staff> getMembersByStatus(Collection statuses=[]) {
        if (statuses) {
            HashSet<StaffStatus> findStatuses =
                new HashSet<>(Helpers.toEnumList(StaffStatus, statuses))
            this.members.findAll { Staff s1 ->
                s1.status in findStatuses
            }
        }
        else { this.members }
    }

    // Property Access
    // ---------------

    @GrailsCompileStatic
    void setLocation(Location l) {
        this.location = l
        this.location?.save()
    }
    boolean getHasInactivePhone() {
        Phone ph = this.phoneWithAnyStatus
        ph ? !ph.isActive : false
    }
    Phone getPhoneWithAnyStatus() {
        PhoneOwnership.createCriteria().list {
            projections { property("phone") }
            eq("type", PhoneOwnershipType.GROUP)
            eq("ownerId", this.id)
        }[0]
    }
    Phone getPhone() {
        PhoneOwnership.createCriteria().list {
            projections { property("phone") }
            phone { isNotNull("numberAsString") }
            eq("type", PhoneOwnershipType.GROUP)
            eq("ownerId", this.id)
        }[0]
    }
}
