package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="Staff", description="A staff member at an organization.")
class Staff {

    def resultFactory
	def springSecurityService

    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    @RestApiObjectField(description="Username of the staff member.")
	String username
    @RestApiObjectField(
        description       = "Password of the staff member.",
        presentInResponse = false)
	String password

    @RestApiObjectField(description="Full name of the staff member.")
	String name
    @RestApiObjectField(description="Email address of the staff member.")
	String email
    @RestApiObjectField(
        description    = "Id of the organization this team belongs to",
        allowedType    = "Number",
        useForCreation = false)
	Organization org
    @RestApiObjectField(
        description  = "Status of the staff member. Allowed: blocked, pending, staff, admin",
        mandatory    = false,
        defaultValue = "pending")
	String status = Constants.STATUS_PENDING
    @RestApiObjectField(
        description = "Personal phone number of the staff member.",
        allowedType = "String")
    PhoneNumber personalPhoneNumber

    @RestApiObjectField(
        description    = "TextUp phone number of the staff member.",
        useForCreation = false,
        allowedType    = "String")
	StaffPhone phone
    @RestApiObjectField(
        description    = "Schedule of the staff member.",
        useForCreation = false)
	Schedule schedule
    @RestApiObjectField(
        description    = "Away message to text back when a text comes in but the staff is unavailable.",
        useForCreation = false)
    String awayMessage = Constants.DEFAULT_AWAY_MESSAGE

    //If manual schedule is true then ignore the Schedule object and
    //look only at the 'available' boolean
    @RestApiObjectField(
        description  = "If the staff member wants to manually manage schedule.",
        mandatory    = false,
        defaultValue = "false")
    boolean manualSchedule = false
    @RestApiObjectField(
        description  = "If the staff member is available. Can only be mutated if the staff member is manually managing the schedule.",
        mandatory    = false,
        defaultValue = "true")
    boolean isAvailable = true

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "phoneId",
            description    = "Id of the phone number to provision as the TextUp number of this staff member",
            useForCreation = false,
            allowedType    = "String",
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "org.id",
            description       = "Id of the organization to associate with",
            allowedType       = "Number",
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "org.name",
            description       = "If creating a new organization, the name of the organization to create and associate with",
            mandatory         = false,
            allowedType       = "String",
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "org.location",
            description       = "If creating a new organization, the location of the organization to create and associate with",
            mandatory         = false,
            allowedType       = "Location",
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "isAvailableNow",
            description    = "If the staff member is available right now.",
            allowedType    = "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "tags",
            description    = "List of tags the staff member's TextUp phone, if any.",
            allowedType    = "List<Tag>",
            useForCreation = false)
    ])
    static transients = []
    static embedded = ["personalPhoneNumber"]
	static constraints = {
		username blank:false, unique:true
		password blank:false
		email email:true
        personalPhoneNumber nullable:true
		status inList:[Constants.STATUS_BLOCKED, Constants.STATUS_PENDING,
            Constants.STATUS_STAFF, Constants.STATUS_ADMIN]
        phone nullable:true
        awayMessage blank:false, size:1..(Constants.TEXT_LENGTH)
	}
	static mapping = {
		password column: '`password`'
	}
    static namedQueries = {
        activeForTeam { Team thisTeam ->
            "in"("id", TeamMembership.staffIdsForTeam(thisTeam).list())
            "in"("status", [Constants.STATUS_STAFF, Constants.STATUS_ADMIN])
        }
        membersForTeam { Team thisTeam, Collection<String> statuses ->
            "in"("id", TeamMembership.staffIdsForTeam(thisTeam).list())
            if (statuses) {
                "in"("status", statuses)
            }
        }
        forOrgAndStatuses { Organization thisOrg, Collection<String> statuses ->
            eq("org", thisOrg)
            if (statuses) "in"("status", statuses)
        }
        forPersonalAndWorkPhoneNums { TransientPhoneNumber personalNum, TransientPhoneNumber workNum ->
            eq("personalPhoneNumber.number", personalNum?.number)
            phone { eq("number.number", workNum?.number) }
        }
    }

	/*
	Has many:
		TeamMembership
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        Staff.withNewSession {
            TeamMembership.where { staff == this }.deleteAll()

            def tags = ContactTag.where { phone == this.phone }
            def contacts = Contact.where { phone == this.phone }
            //delete tag memberships, must come before
            //deleting ContactTag and Contact
            new DetachedCriteria(TagMembership).build {
                "in"("tag", tags.list())
            }.deleteAll()
            //must be before we delete our contacts FOR RECORD DELETION
            def associatedRecordIds = new DetachedCriteria(Contact).build {
                projections { property("record.id") }
                eq("phone", this.phone)
            }.list()
            //delete contacts' numbers
            new DetachedCriteria(ContactNumber).build {
                "in"("contact", contacts.list())
            }.deleteAll()
            //delete shared contacts
            SharedContact.where { sharedBy == this.phone || sharedWith == this.phone }.deleteAll()
            //delete contact and contact tags
            contacts.deleteAll()
            tags.deleteAll()
            //delete records associated with contacts, must
            //come after contacts are deleted
            new DetachedCriteria(Record).build {
                "in"("id", associatedRecordIds)
            }.deleteAll()
        }
    }
    def afterDelete() {
        Staff.withNewSession {
            StaffPhone.where { id == this.phone.id }.deleteAll()
            Schedule.where { id == this.schedule.id }.deleteAll()
        }
    }

    def beforeValidate() {
        if (!this.schedule) {
            this.schedule = new WeeklySchedule([:])
            this.schedule.save()
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    /*
    Permissions
     */
    Result<Staff> approve() {
        this.status = Constants.STATUS_STAFF
        resultFactory.success(this)
    }
    Result<Staff> block() {
        this.status = Constants.STATUS_BLOCKED
        resultFactory.success(this)
    }
    Result<Staff> promoteToAdmin() {
        if (this.status == Constants.STATUS_STAFF) {
            this.status = Constants.STATUS_ADMIN
            resultFactory.success(this)
        }
        else { resultFactory.failWithMessage("staff.error.notYetApproved") }
    }
    Result<Staff> demoteFromAdmin() {
        if (this.status == Constants.STATUS_ADMIN) {
            this.status = Constants.STATUS_STAFF
            resultFactory.success(this)
        }
        else { resultFactory.failWithMessage("staff.error.notAdmin") }
    }

    /*
    Memberships
     */
    List<Team> getTeams(Map params=[:]) {
        Team.forStaff(this).list(params) ?: []
    }
    Result<TeamMembership> addToTeam(String teamName) {
        Team team = Team.findByOrgAndName(this.org, teamName)
        if (team) { addToTeam(team) }
        else { resultFactory.failWithMessage("staff.error.teamNotFound", [teamName]) }
    }
    Result<TeamMembership> addToTeam(Team team) {
        TeamMembership m = TeamMembership.findByStaffAndTeam(this, team)
        if (m) { resultFactory.success(m) }
        else {
            m = new TeamMembership(staff:this, team:team)
            if (m.save()) { resultFactory.success(m) }
            else { resultFactory.failWithValidationErrors(m.errors) }
        }
    }
    Result<Team> removeFromTeam(String teamName) {
        Team team = Team.findByOrgAndName(this.org, teamName)
        if (team) { removeFromTeam(team) }
        else { resultFactory.failWithMessage("staff.error.teamNotFound", [teamName]) }
    }
    Result<Team> removeFromTeam(Team team) {
        TeamMembership m = TeamMembership.findByStaffAndTeam(this, team)
        if (m) { m.delete() }
        resultFactory.success(team)
    }

    /*
    Schedule
     */
    boolean isAvailableNow() {
        manualSchedule ? isAvailable : schedule.isAvailableNow()
    }
    Result<Boolean> isAvailableAt(DateTime dt) {
        if (!manualSchedule) { resultFactory.success(schedule.isAvailableAt(dt)) }
        else { resultFactory.failWithMessage("staff.error.scheduleInfoUnavailable") }
    }
    Result<ScheduleChange> nextChange() {
        if (!manualSchedule) {
            schedule.nextChange()
        }
        else { resultFactory.failWithMessage("staff.error.scheduleInfoUnavailable") }
    }
    Result<DateTime> nextAvailable() {
        if (!manualSchedule) {
            schedule.nextAvailable()
        }
        else { resultFactory.failWithMessage("staff.error.scheduleInfoUnavailable") }
    }
    Result<DateTime> nextUnavailable() {
        if (!manualSchedule) {
            schedule.nextUnavailable()
        }
        else { resultFactory.failWithMessage("staff.error.scheduleInfoUnavailable") }
    }
    Result<Schedule> updateSchedule(Map params) {
        schedule.update(params)
    }

    /*
    SpringSecurityCore methods
     */
	Set<Role> getAuthorities() {
		StaffRole.findAllByStaff(this).collect { it.role }
	}
	def beforeInsert() {
		encodePassword()
	}
	def beforeUpdate() {
		if (isDirty('password')) {
			encodePassword()
		}
	}
	protected void encodePassword() {
		password = springSecurityService?.passwordEncoder ? springSecurityService.encodePassword(password) : password
	}

    /////////////////////
    // Property Access //
    /////////////////////

    void setUsername(String un) {
        this.username = un?.toLowerCase()
    }

    void setSchedule(Schedule s) {
        this.schedule = s
        this.schedule?.save()
    }

    void setPersonalPhoneNumberAsString(String num) {
        if (this.personalPhoneNumber) {
            this.personalPhoneNumber.number = num
        }
        else {
            this.personalPhoneNumber = new PhoneNumber(number:num)
        }
        this.personalPhoneNumber.save()
    }
    void setPersonalPhoneNumber(PhoneNumber pNum) {
        this.personalPhoneNumber = pNum
        this.personalPhoneNumber?.save()
    }

    void setPhone(StaffPhone p) {
        this.phone = p
    	if (this.phone) {
    		this.phone.ownerId = this.id
            if (this.phone.validate()) { this.phone.save() }
    	}
    }
}
