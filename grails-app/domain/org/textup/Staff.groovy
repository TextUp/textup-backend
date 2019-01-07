package org.textup

import grails.compiler.GrailsTypeChecked
import grails.plugin.springsecurity.SpringSecurityService
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.springframework.security.authentication.encoding.PasswordEncoder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Staff implements Schedulable, WithId {

	SpringSecurityService springSecurityService
    PasswordEncoder passwordEncoder

    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    boolean isAvailable = true
    boolean manualSchedule = true
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    Organization org
    Schedule schedule
    StaffStatus status = StaffStatus.PENDING
    String email
    String lockCode = Constants.DEFAULT_LOCK_CODE
    String name
    String password
    String personalPhoneAsString
    String username

    static transients = ["personalPhoneNumber", "phone", "springSecurityService", "passwordEncoder"]
    static mapping = {
        whenCreated type: PersistentDateTime
        org lazy: false
        password column: "`password`"
        schedule cascade: "save-update"
    }
	static constraints = {
		username blank:false, unique:true, validator: { String un, Staff s1 ->
            if (!(un ==~ /^[-_=@.,;A-Za-z0-9]+$/)) { ["format"] } // for Pusher channel
        }
		password blank:false
        lockCode blank:false
		email email:true
        personalPhoneAsString blank:true, nullable:true, validator:{ String val, Staff obj ->
            if (val && !(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
        schedule cascadeValidation: true
	}

    def beforeValidate() {
        if (!schedule) {
            schedule = new WeeklySchedule()
        }
    }

    def beforeInsert() {
        encodePassword()
        encodeLockCode()
    }

    def beforeUpdate() {
        if (isDirty("password")) {
            encodePassword()
        }
        if (isDirty("lockCode")) {
            encodeLockCode()
        }
    }

    // Methods
    // -------

    boolean isLockCodeValid(String inputLockCode) {
        passwordEncoder.isPasswordValid(lockCode, inputLockCode, null)
    }

    boolean isAvailableNow() {
        manualSchedule ? isAvailable : schedule.isAvailableNow()
    }

    Result<Schedule> updateSchedule(Map params) {
        schedule.update(params)
    }

    boolean sharesTeamWith(Staff s1) {
        HashSet<Team> myTeams = new HashSet<>(getTeams())
        s1?.getTeams()?.any { it in myTeams }
    }

    Author toAuthor() {
        new Author(id: id, type: AuthorType.STAFF, name: name)
    }

    // Properties
    // ----------

    Collection<Staff> getCanShareWith(Collection<String> statuses=[]) {
        HashSet<Staff> staffCanShare = new HashSet<>()
        getTeams().each { Team t1 ->
            Collection<Staff> members = t1.getMembersByStatus(statuses)
            // add only staff members that have a phone!
            staffCanShare.addAll(members.findAll { it.phone })
        }
        staffCanShare.remove(this)
        staffCanShare
    }

    Set<Role> getAuthorities() { StaffRole.findAllByStaff(this).collect { it.role } }

    void setUsername(String un) { username = un?.toLowerCase() }

    void setPersonalPhoneNumber(BasePhoneNumber num) { personalPhoneAsString = num?.number }

    PhoneNumber getPersonalPhoneNumber() { PhoneNumber.create(personalPhoneAsString) }

    String getChannelName() { username ? "private-${username}" : "" }

    // Helpers
    // -------

    protected void encodeLockCode() {
        lockCode = passwordEncoder ?
            passwordEncoder.encodePassword(lockCode, null) : lockCode
    }

    protected void encodePassword() {
        password = springSecurityService?.passwordEncoder ?
            springSecurityService.encodePassword(password) : password
    }
}
