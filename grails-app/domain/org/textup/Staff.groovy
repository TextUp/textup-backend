package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.plugin.springsecurity.SpringSecurityService
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.types.PhoneOwnershipType
import org.textup.types.StaffStatus
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.ScheduleChange

@GrailsTypeChecked
@EqualsAndHashCode
@RestApiObject(name="Staff", description="A staff member at an organization.")
class Staff {

    ResultFactory resultFactory
	SpringSecurityService springSecurityService

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
            apiFieldName  = "phone",
            description   = "TextUp phone number",
            allowedType   = "String"),
        @RestApiObjectField(
            apiFieldName  = "awayMessage",
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
    static transients = ["personalPhoneNumber", "phone", "resultFactory",
        "springSecurityService"]
	static constraints = {
		username blank:false, unique:true
		password blank:false
		email email:true
        personalPhoneAsString blank:true, nullable:true, validator:{ String val, Staff obj ->
            if (val && !(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
	}
	static mapping = {
		password column: '`password`'
	}

	// Events
    // ------

    def beforeValidate() {
        if (!this.schedule) {
            this.schedule = new WeeklySchedule([:])
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

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
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
        HashSet<Team> myTeams = new HashSet<>(this.getTeams())
        s1?.getTeams()?.any { it in myTeams }
    }

    // Sharing
    // -------

    Collection<Staff> getCanShareWith(Collection<String> statuses=[]) {
        HashSet<Staff> staffCanShare = new HashSet<>()
        this.getTeams().each { Team t1 ->
            staffCanShare.addAll(t1.getMembersByStatus(statuses))
        }
        staffCanShare.remove(this)
        staffCanShare
    }


    // Property Access
    // ---------------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    int countTeams() {
        Team.forStaffs([this]).count()
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
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
    void setPersonalPhoneNumber(BasePhoneNumber num) {
        this.personalPhoneAsString = num?.number
    }
    PhoneNumber getPersonalPhoneNumber() {
        new PhoneNumber(number:this.personalPhoneAsString)
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    Phone getPhone() {
        PhoneOwnership.createCriteria().list {
            projections { property("phone") }
            eq("type", PhoneOwnershipType.INDIVIDUAL)
            eq("ownerId", this.id)
        }[0]
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    List<Phone> getAllPhones() {
        List<Team> teams = this.teams
        PhoneOwnership.createCriteria().list {
            projections { property("phone") }
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
