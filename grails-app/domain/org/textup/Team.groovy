package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="Team", description="A team at an organization.")
class Team {

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
            apiFieldField = "phone",
            description   = "TextUp phone number",
            allowedType   = "String"),
        @RestApiObjectField(
            apiFieldField = "awayMessage",
            description   = "Away message when no staff members in this team \
                are available to respond to texts or calls",
            allowedType   = "String")
    ])
    static transients = ["phone"]
    static hasMany = [members:Staff]
    static mapping = {
        members lazy:false, cascade:"save-update"
    }
    static constraints = {
    	name blank:false, validator: { val, obj ->
            //within an Org, team name must be unique
            if (obj.hasExistingTeamName(val)) ["duplicate", obj.org?.name]
        }
        hexColor shared:"hexColor"
    }
    static namedQueries = {
        forStaffs { List<Staff> staffs ->
            members {
                if (staffs) {
                    "in"("id", staffs.*id)
                }
                else { eq("id", null) }
            }
        }
    }

    // Validator
    // ---------

    private boolean hasExistingTeamName(String teamName) {
        boolean duplicateTeam = false
        Team.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                Team t = Team.findByOrgAndName(this.org, teamName)
                if (t && t.id != this.id) { duplicateTeam = true }
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        duplicateTeam
    }

    // Members
    // -------

    List<Staff> getActiveMembers() {
        this.members.findAll { Staff s1 ->
            s1.status == StaffStatus.STAFF || s1.status == StaffStatus.ADMIN
        }
    }
    List<Staff> getMembers(Collection statuses=[]) {
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

    void setLocation(Location l) {
        this.location = l
        this.location?.save()
    }
    void setPhone(Phone p1) {
        PhoneOwnership own = PhoneOwnership.findByOwnerIdAndType(this.id,
            PhoneOwnershipType.GROUP) ?:
            new PhoneOwnership(ownerId:this.id, type:PhoneOwnershipType.GROUP)
        own.phone = p1
        if (own.phone.validate()) { own.phone.save() }
    }
    Phone getPhone() {
        PhoneOwnership.createCriteria().list {
            propjections { property("phone") }
            eq("type", PhoneOwnershipType.GROUP)
            eq("ownerId", this.id)
        }[0]
    }
}
