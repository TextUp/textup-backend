package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class Staff implements Schedulable, WithId, Saveable {

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

    static transients = ["personalPhoneNumber"]
    static mapping = {
        cache usage: "read-write", include: "non-lazy"
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

    boolean isAvailableNow() {
        manualSchedule ? isAvailable : schedule.isAvailableNow()
    }

    Result<Schedule> updateSchedule(Map params) {
        schedule.update(params)
    }

    Author toAuthor() {
        new Author(id: id, type: AuthorType.STAFF, name: name)
    }

    // Properties
    // ----------

    Set<Role> getAuthorities() { StaffRole.findAllByStaff(this).collect { it.role } }

    void setUsername(String un) { username = un?.toLowerCase() }

    void setPersonalPhoneNumber(BasePhoneNumber num) { personalPhoneAsString = num?.number }

    PhoneNumber getPersonalPhoneNumber() { PhoneNumber.create(personalPhoneAsString) }

    String getChannelName() { username ? "private-${username}" : "" }

    // Helpers
    // -------

    protected void encodeLockCode() { lockCode = AuthUtils.encodeSecureString(lockCode) }

    protected void encodePassword() { password = AuthUtils.encodeSecureString(password) }
}
