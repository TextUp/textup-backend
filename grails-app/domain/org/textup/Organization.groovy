package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.util.*

@EqualsAndHashCode
@RestApiObject(name="Organization", description="An organization of staff members and teams.")
class Organization implements WithId {

    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)

    @RestApiObjectField(
        description    = "Name of the organization",
        useForCreation = true,
        allowedType    = "String")
	String name

    @RestApiObjectField(
        description    = "Location of the organization",
        useForCreation = true,
        allowedType    = "Location")
	Location location

    @RestApiObjectField(
        description    = "Status of the overall organization. \
            One of REJECTED, PENDING, or APPROVED",
        useForCreation = false)
    OrgStatus status = OrgStatus.PENDING

    @RestApiObjectField(
        description    = "Time to wait to lock all users' screens in milliseconds \
            minimum is 15 seconds, maximum is 60 seconds",
        useForCreation = true,
        allowedType    = "int")
    int timeout = Constants.DEFAULT_LOCK_TIMEOUT_MILLIS

    @RestApiObjectField(
        description    = "Message to append to the end of all text message away messages",
        useForCreation = true,
        allowedType    = "String")
    String awayMessageSuffix = Constants.DEFAULT_AWAY_MESSAGE_SUFFIX

    @RestApiObjectField(
        apiFieldName   = "numAdmins",
        description    = "Number of admins this organization has",
        useForCreation = false,
        allowedType    = "Number")
    static constraints = {
    	name blank:false, validator:{ String val, Organization obj ->
    		//must have unique (name, location) combination
            Closure<Boolean> hasNameAndLocation = {
                List<Organization> orgs = Organization.createCriteria().list {
                    eq("name", val)
                    location {
                        eq("lat", obj.location?.lat)
                        eq("lon", obj.location?.lon)
                    }
                }
                // if there are organizations found, then check to see if these organizations
                // are DIFFERENT than the one we are trying to save. If there are organizations
                // that have the same name and location that aren't this same organization
                // then we have a duplicate
                !orgs.isEmpty() && orgs.any { Organization org -> org.id != obj.id }
            }
    		if (val && obj.location && Utils.<Boolean>doWithoutFlush(hasNameAndLocation)) {
                ["duplicate", obj.location?.address]
            }
    	}
        status blank: false, nullable: false
        timeout min: Constants.DEFAULT_LOCK_TIMEOUT_MILLIS, max: Constants.MAX_LOCK_TIMEOUT_MILLIS
        // leave one character for the space for joining this suffix with an away message
        awayMessageSuffix nullable: true, blank: true, size: 1..(Constants.TEXT_LENGTH - 1)
    }
    static mapping = {
        whenCreated type:PersistentDateTime
    }

    // Staff
    // -----

    /**
     * Creates a staff for this organization
     * @param  params Map of parameters for this staff with some exceptions:
     *                personalPhoneNumber must be passed in with the key
     *                'personalPhoneAsString' and this methods DOES NOT
     *                support adding a StaffPhone.
     * @return        Result object containing the new Staff is success,
     *                ValidationError otherwise
     */
    @GrailsTypeChecked
    Result<Staff> addStaff(Map params) {
        Staff s = new Staff()
        //prevent intervening on these internal fields
        ["enabled", "accountExpired", "accountLocked", "passwordExpired"].each {
            params.remove(it)
        }
        s.properties = params
        s.personalPhoneAsString = params.personalPhoneAsString
        s.org = this
        if (s.save()) { IOCUtils.resultFactory.success(s) }
        else { IOCUtils.resultFactory.failWithValidationErrors(s.errors) }
    }

    // Teams
    // -----

    @GrailsTypeChecked
    Result<Team> addTeam(Map params) {
        Team t = new Team()
        t.properties = params
        t.org = this
        if (t.save()) { IOCUtils.resultFactory.success(t) }
        else { IOCUtils.resultFactory.failWithValidationErrors(t.errors) }
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked
    void setLocation(Location l) {
        this.location = l
        l.save()
    }
    int countStaff(String searchString) {
        Staff.ilikeForOrgAndQuery(this, StringUtils.toQuery(searchString)).count()
    }
    List<Staff> getStaff(String searchString, Map params = [:]) {
        Staff.ilikeForOrgAndQuery(this, StringUtils.toQuery(searchString)).list(params)
    }
    @GrailsTypeChecked
    List<Staff> getAdmins(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.ADMIN]])
    }
    @GrailsTypeChecked
    List<Staff> getStaff(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.STAFF]])
    }
    @GrailsTypeChecked
    List<Staff> getPending(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.PENDING]])
    }
    @GrailsTypeChecked
    List<Staff> getBlocked(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.BLOCKED]])
    }
    @GrailsTypeChecked
    int countPeople(Map params=[:]) {
        List<StaffStatus> statusEnums =
            TypeConversionUtils.toEnumList(StaffStatus, params.statuses)
        if (statusEnums) {
            Staff.countByOrgAndStatusInList(this, statusEnums)
        }
        else { Staff.countByOrg(this) }
    }
    @GrailsTypeChecked
    List<Staff> getPeople(Map params=[:]) {
        List<StaffStatus> statusEnums =
            TypeConversionUtils.toEnumList(StaffStatus, params.statuses)
        if (statusEnums) {
            Staff.findAllByOrgAndStatusInList(this, statusEnums, params)
        }
        else { Staff.findAllByOrg(this, params) }
    }
    @GrailsTypeChecked
    int countTeams() {
        Team.countByOrgAndIsDeleted(this, false)
    }
    @GrailsTypeChecked
    List<Team> getTeams(Map params=[:]) {
        Team.findAllByOrgAndIsDeleted(this, false, params)
    }
}
