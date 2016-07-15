package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.plugin.springsecurity.SpringSecurityService
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.types.AuthorType
import org.textup.types.PhoneOwnershipType
import org.textup.types.StaffStatus
import org.textup.validator.Author
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.ScheduleChange

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
        description    = "The organization this staff member belongs to",
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
            description   = "TextUp phone",
            allowedType   = "Phone"),
        @RestApiObjectField(
            apiFieldName = "hasInactivePhone",
            description  = "Whether this staff has an inactive TextUp phone",
            allowedType  = "Boolean",
            mandatory    = false),
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
		username blank:false, unique:true, validator: { String un, Staff s1 ->
            if (!(un ==~ /^[-_=@.,;A-Za-z0-9]+$/)) { ["format"] } // for Pusher channel
        }
		password blank:false
		email email:true
        personalPhoneAsString blank:true, nullable:true, validator:{ String val, Staff obj ->
            if (val && !(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
	}
	static mapping = {
		password column: '`password`'
	}
    static namedQueries = {
        ilikeForOrgAndQuery { Organization org, String query ->
            eq("org", org)
            or {
                ilike("name", query)
                ilike("username", query)
                ilike("email", query)
            }
            "in"("status", [StaffStatus.STAFF, StaffStatus.ADMIN])
        }
    }

	// Events
    // ------

    @GrailsTypeChecked
    def beforeValidate() {
        if (!this.schedule) {
            this.schedule = new WeeklySchedule([:])
        }
    }

    // Schedule
    // --------

    @GrailsTypeChecked
    boolean isAvailableNow() {
        manualSchedule ? isAvailable : schedule.isAvailableNow()
    }
    @GrailsTypeChecked
    Result<Boolean> isAvailableAt(DateTime dt) {
        if (!manualSchedule) { resultFactory.success(schedule.isAvailableAt(dt)) }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    @GrailsTypeChecked
    Result<ScheduleChange> nextChange(String timezone=null) {
        if (!manualSchedule) {
            schedule.nextChange(timezone)
        }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    @GrailsTypeChecked
    Result<DateTime> nextAvailable(String timezone=null) {
        if (!manualSchedule) {
            schedule.nextAvailable(timezone)
        }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    @GrailsTypeChecked
    Result<DateTime> nextUnavailable(String timezone=null) {
        if (!manualSchedule) {
            schedule.nextUnavailable(timezone)
        }
        else { resultFactory.failWithMessage("staff.scheduleInfoUnavailable") }
    }
    @GrailsTypeChecked
    Result<Schedule> updateSchedule(Map params) {
        schedule.update(params)
    }

    // SpringSecurityCore methods
    // --------------------------

	Set<Role> getAuthorities() {
		StaffRole.findAllByStaff(this).collect { it.role }
	}
    @GrailsTypeChecked
	def beforeInsert() {
		encodePassword()
	}
    @GrailsTypeChecked
	def beforeUpdate() {
		if (isDirty('password')) {
			encodePassword()
		}
	}
    @GrailsTypeChecked
	protected void encodePassword() {
		password = springSecurityService?.passwordEncoder ?
            springSecurityService.encodePassword(password) : password
	}

    // Team
    // ----

    @GrailsTypeChecked
    boolean sharesTeamWith(Staff s1) {
        HashSet<Team> myTeams = new HashSet<>(this.getTeams())
        s1?.getTeams()?.any { it in myTeams }
    }

    // Sharing
    // -------

    @GrailsTypeChecked
    Collection<Staff> getCanShareWith(Collection<String> statuses=[]) {
        HashSet<Staff> staffCanShare = new HashSet<>()
        this.getTeams().each { Team t1 ->
            Collection<Staff> members = t1.getMembersByStatus(statuses)
            // add only staff members that have a phone!
            staffCanShare.addAll(members.findAll { it.phone })
        }
        staffCanShare.remove(this)
        staffCanShare
    }


    // Property Access
    // ---------------

    @GrailsTypeChecked
    Author toAuthor() {
        new Author(id:this.id, type:AuthorType.STAFF, name:this.name)
    }

    int countTeams() {
        Team.forStaffs([this]).count()
    }
    List<Team> getTeams(Map params=[:]) {
        Team.forStaffs([this]).list(params)
    }
    @GrailsTypeChecked
    void setUsername(String un) {
        this.username = un?.toLowerCase()
    }
    @GrailsTypeChecked
    void setSchedule(Schedule s) {
        this.schedule = s
        this.schedule?.save()
    }
    @GrailsTypeChecked
    void setPersonalPhoneNumber(BasePhoneNumber num) {
        this.personalPhoneAsString = num?.number
    }
    @GrailsTypeChecked
    PhoneNumber getPersonalPhoneNumber() {
        new PhoneNumber(number:this.personalPhoneAsString)
    }
    @GrailsTypeChecked
    boolean getHasInactivePhone() {
        Phone ph = this.phoneWithAnyStatus
        ph ? !ph.isActive : false
    }
    Phone getPhoneWithAnyStatus() {
        PhoneOwnership.createCriteria().list {
            projections { property("phone") }
            eq("type", PhoneOwnershipType.INDIVIDUAL)
            eq("ownerId", this.id)
        }[0]
    }
    Phone getPhone() {
        PhoneOwnership.createCriteria().list {
            projections { property("phone") }
            phone { isNotNull("numberAsString") }
            eq("type", PhoneOwnershipType.INDIVIDUAL)
            eq("ownerId", this.id)
        }[0]
    }
    List<Phone> getAllPhones() {
        List<Team> teams = this.teams
        PhoneOwnership.createCriteria().list {
            projections { property("phone") }
            phone { isNotNull("numberAsString") }
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
