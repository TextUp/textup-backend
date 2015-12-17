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
    @RestApiObjectField(
        description    = "Shared team phone number",
        allowedType    = "String",
        useForCreation = false,
        mandatory      = false)
	TeamPhone phone //teams can optionally have a shared phone
    @RestApiObjectField(description="Location this team is based at. Default same as organization.")
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
            description       = "List of some actions to perform on staff with relation to this team",
            allowedType       = "List<[teamAction]>",
            useForCreation    = false,
            presentInResponse = false)
    ])
    static transients = []
    static constraints = {
    	name blank:false, validator: { val, obj ->
            //within an Org, team name must be unique
            if (obj.hasExistingTeamName(val)) ["duplicate", obj.org?.name]
        }
        phone nullable:true
        hexColor blank:false, nullable:false, validator:{ val, obj ->
            //String must be a valid hex color
            if (!(val ==~ /^#(\d|\w){3}/ || val ==~ /^#(\d|\w){6}/)) { ["invalidHex"] }
        }
    }
    static namedQueries = {
        forStaff { Staff thisStaff ->
            DetachedCriteria teamIds = new DetachedCriteria(TeamMembership).build {
                projections { property("team.id") }
                eq("staff", thisStaff)
            }
            "in"("id", teamIds)
        }
        forStaffId { Long thisStaffId ->
            DetachedCriteria teamIds = new DetachedCriteria(TeamMembership).build {
                projections { property("team.id") }
                eq("staff.id", thisStaffId)
            }
            "in"("id", teamIds)
        }
        teamIdsForStaffId { Long thisStaffId ->
            forStaffId(thisStaffId)
            projections { property("id") }
        }
        teamPhoneIdsForStaffId { Long thisStaffId ->
            forStaffId(thisStaffId)
            projections { property("phone.id") }
        }
        forOrg { Organization thisOrg -> eq("org", thisOrg) }
        forPhone { TeamPhone thisPhone -> eq("phone", thisPhone) }
    }

    /*
	Has many:
		TeamMembership
	*/

    ////////////
    // Events //
    ////////////

    //TODO: update with new classes!!!

    def beforeDelete() {
        Team.withNewSession {
            TeamMembership.where { team == this }.deleteAll()

            def tags = ContactTag.where { phone == this.phone }
            def contacts = Contact.where { phone == this.phone }
            //delete tag memberships, must come before
            //deleting ContactTag and Contact
            new DetachedCriteria(TagMembership).build {
                "in"("tag", tags.list())
            }.deleteAll()
            //must be before we delete our contacts FOR RECORD DELETION
            def contactRecords = new DetachedCriteria(Contact).build {
                projections { property("record") }
                eq("phone", this.phone)
            }.list()
            def tagRecords = new DetachedCriteria(TeamContactTag).build {
                projections { property("record") }
                eq("phone", this.phone)
            }.list()
            List<Record> allRecords = contactRecords + tagRecords
            //delete contacts' numbers
            new DetachedCriteria(ContactNumber).build {
                "in"("contact", contacts.list())
            }.deleteAll()
            //delete contact and contact tags
            contacts.deleteAll()
            tags.deleteAll()
            //delete all receipts before deleting items
            def items = new DetachedCriteria(RecordItem).build {
                "in"("record", allRecords)
            }
            new DetachedCriteria(RecordItemReceipt).build {
                "in"("item", items.list())
            }.deleteAll()
            //delete all record items before deleting record
            items.deleteAll()
            //delete records associated with contacts and tags, must
            //come after contacts are deleted
            new DetachedCriteria(Record).build {
                "in"("id", allRecords*.id)
            }.deleteAll()
        }
    }
    def afterDelete() {
        Team.withNewSession {
            if (this.phone) {
                PhoneNumber.where { id == this.phone.number.id }.deleteAll()
                TeamPhone.where { id == this.phone.id }.deleteAll()
            }
            Location.where { id == this.location.id }.deleteAll()
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    /*
    Members
     */
    int countActiveMembers() {
        Staff.activeForTeam(this).count()
    }
    List<Staff> getActiveMembers(Map params=[:]) {
        Staff.activeForTeam(this).list(params)
    }
    int countMembers(String s) { countMembers([s]) }
    int countMembers(List<String> statuses = []) {
        Staff.membersForTeam(this, statuses).count()
    }
    List<Staff> getMembers(Map params=[:]) {
        List<String> statuses = params.status ?: []
        Staff.membersForTeam(this, statuses).list(params)
    }

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

    /////////////////////
    // Property Access //
    /////////////////////

    //For some reason, saving the location here is okay
    //but saving in the setteraves many duplicates for
    //when adding numbers as strings
    void setLocation(Location l) {
        this.location = l
        this.location?.save()
    }

}
