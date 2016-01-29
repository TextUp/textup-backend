package org.textup

import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.enum.*

@EqualsAndHashCode
@RestApiObject(name="Staff", description="A staff member at an organization.")
class Staff {

    def resultFactory
	def springSecurityService

    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    String personalPhoneAsString

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
        description  = "Status of the staff member. Allowed: BLOCKED, PENDING, STAFF, ADMIN",
        mandatory    = false,
        allowedType  = "String",
        defaultValue = "PENDING")
	StaffStatus status = StaffStatus.PENDING

    @RestApiObjectField(
        description    = "Schedule of the staff member.",
        useForCreation = false)
	Schedule schedule

    //If manual schedule is true then ignore the Schedule object and
    //look only at the 'available' boolean
    @RestApiObjectField(
        description  = "If the staff member wants to manually manage schedule.",
        mandatory    = false,
        defaultValue = "false")
    boolean manualSchedule = false

    @RestApiObjectField(
        description  = "If the staff member is available. Can only be mutated \
            if the staff member is manually managing the schedule.",
        mandatory    = false,
        defaultValue = "true")
    boolean isAvailable = true

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldField = "phone",
            description   = "TextUp phone number",
            allowedType   = "String"),
        @RestApiObjectField(
            apiFieldField = "awayMessage",
            description   = "Away message when no staff members in this team \
                are available to respond to texts or calls",
            allowedType   = "String"),
        @RestApiObjectField(
            apiFieldName   = "phoneId",
            description    = "Id of the phone number to provision as the \
                TextUp number of this staff member",
            useForCreation = false,
            allowedType    = "String",
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "orgName",
            description       = "If creating a new organization, the name of \
                the organization to create and associate with",
            mandatory         = false,
            allowedType       = "String",
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
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName   = "teams",
            description    = "List of teams the staff member is a member of.",
            allowedType    = "List<Team>",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName = "personalPhoneNumber",
            description  = "Personal phone number of the staff member.",
            allowedType  = "String")
    ])
    static transients = ['personalPhoneNumber', 'phone']
	static constraints = {
		username blank:false, unique:true
		password blank:false
		email email:true
        personalPhoneAsString nullable:true, shared: 'phoneNumber'
        phone nullable:true
	}
	static mapping = {
		password column: '`password`'
	}
    static namedQueries = {
        forOrgAndStatuses { Organization thisOrg, Collection<StaffStatus> statuses ->
            eq("org", thisOrg)
            if (statuses) { "in"("status", statuses) }
            else { eq("status", null) }
        }
    }

	// Events
    // ------

    def beforeValidate() {
        if (!this.schedule) {
            this.schedule = new WeeklySchedule([:])
            this.schedule.save()
        }
    }

    // Schedule
    // --------

    boolean isAvailableNow() {
        manualSchedule ? isAvailable : schedule.isAvailableNow()
    }
    Result<Boolean> isAvailableAt(DateTime dt) {
        if (!manualSchedule) { resultFactory.success(schedule.isAvailableAt(dt)) }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    Result<ScheduleChange> nextChange(String timezone=null) {
        if (!manualSchedule) {
            schedule.nextChange(timezone)
        }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    Result<DateTime> nextAvailable(String timezone=null) {
        if (!manualSchedule) {
            schedule.nextAvailable(timezone)
        }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    Result<DateTime> nextUnavailable(String timezone=null) {
        if (!manualSchedule) {
            schedule.nextUnavailable(timezone)
        }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    Result<Schedule> updateSchedule(Map params) {
        schedule.update(params)
    }

    // SpringSecurityCore methods
    // --------------------------

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
		password = springSecurityService?.passwordEncoder ?
            springSecurityService.encodePassword(password) : password
	}

    // Team
    // ----

    boolean sharesTeamWith(Staff s1) {
        HashSet<Teams> myTeams = new HashSet<>(this.teams)
        s1?.teams?.any { it in myTeams }
    }

    // Sharing
    // -------

    Collection<Staff> getCanShareWith(Collection<String> statuses=[]) {
        HashSet<Staff> staffCanShare = new HashSet<>()
        this.teams.each { Team t1 ->
            staffCanShare.addAll(t1.getMembers(statuses))
        }
        staffCanShare
    }


    // Property Access
    // ---------------

    int countTeams() {
        Team.forStaffs([this]).count()
    }
    List<Team> getTeams(Map params=[:]) {
        Team.forStaffs([this]).list(params)
    }
    void setUsername(String un) {
        this.username = un?.toLowerCase()
    }
    void setSchedule(Schedule s) {
        this.schedule = s
        this.schedule?.save()
    }
    void setPersonalPhoneNumber(PhoneNumber num) {
        this.personalPhoneAsString = num?.number
    }
    PhoneNumber getPersonalPhoneNumber() {
        new PhoneNumber(number:this.personalPhoneAsString)
    }
    void setPhone(Phone p1) {
        PhoneOwnership own = PhoneOwnership.findByOwnerIdAndType(this.id,
            PhoneOwnershipType.INDIVIDUAL) ?:
            new PhoneOwnership(ownerId:this.id, type:PhoneOwnershipType.INDIVIDUAL)
        own.phone = p1
        if (own.phone.validate()) { own.phone.save() }
    }
    Phone getPhone() {
        PhoneOwnership.createCriteria().list {
            propjections { property("phone") }
            eq("type", PhoneOwnershipType.INDIVIDUAL)
            eq("ownerId", this.id)
        }[0]
    }
    List<Phone> getAllPhones() {
        List<Team> teams = this.teams
        PhoneOwnership.createCriteria().list {
            propjections { property("phone") }
            or {
                and {
                    eq("type", PhoneOwnershipType.INDIVIDUAL)
                    eq("ownerId", this.id)
                }
                if (teams) {
                    and{
                        eq("type", PhoneOwnershipType.GROUP)
                        "in"("ownerId", teams*.id)
                    }
                }
            }
        }
    }
}
