package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.restapidoc.annotation.*
import org.textup.enum.*

@EqualsAndHashCode
@RestApiObject(name="Organization", description="An organization of staff members and teams.")
class Organization {

    def resultFactory

    @RestApiObjectField(description="Name of the organization")
	String name

    @RestApiObjectField(description="Location of the organization")
	Location location

    OrgStatus status = OrgStatus.PENDING

    @RestApiObjectField(
        apiFieldName   = "numAdmins",
        description    = "Number of admins this organization has",
        useForCreation = false,
        allowedType    = "Number")
    static transients = []
    static constraints = {
    	name blank:false, validator:{ val, obj ->
    		//must have unique (name, location) combination
    		if (obj.hasNameAndLocation(val, obj.location)) {
                ["duplicate", obj.location?.address]
            }
    	}
        status blank:false, nullable:false
    }
    static namedQueries = {
        ilikeForNameAndAddress { String query ->
            or {
                ilike("name", query)
                location { ilike("address", query) }
            }
            eq("status", OrgStatus.APPROVED)
        }
    }

	/*
	Has many:
		Staff
		Team
	*/

    // Validator
    // ---------

    private boolean hasNameAndLocation(String n, Location loc) {
        if ([n, loc].any { it == null }) return false
        boolean hasDuplicate = false
        Organization.withNewSession { session ->
            session.flushMode = FlushMode.MANUAL
            try {
                Organization org = Organization.where {
                    eq("name", n)
                    location {
                        eq("lat", loc.lat)
                        eq("lon", loc.lon)
                    }
                }.get()
                if (org && org.id != this.id) { hasDuplicate = true }
            }
            catch (e) { session.flushMode = FlushMode.AUTO }
            finally { session.flushMode = FlushMode.AUTO }
        }
        hasDuplicate
    }

    // Staff
    // -----

    /**
     * Creates a staff for this organization
     * @param  params Map of parameters for this staff with some exceptions:
     *                personalPhoneNumber must be passed in with the key
     *                'personalPhoneNumberAsString' and this methods DOES NOT
     *                support adding a StaffPhone.
     * @return        Result object containing the new Staff is success,
     *                ValidationError otherwise
     */
    Result<Staff> addStaff(Map params) {
        Staff s = new Staff()
        //prevent intervening on these internal fields
        ["enabled", "accountExpired", "accountLocked", "passwordExpired"].each {
            params.remove(it)
        }
        s.properties = params
        s.personalPhoneNumberAsString = params.personalPhoneNumberAsString
        s.org = this
        if (s.save()) { resultFactory.success(s) }
        else { resultFactory.failWithValidationErrors(s.errors) }
    }

    // Teams
    // -----

    Result<Team> addTeam(Map params) {
        Team t = new Team()
        t.properties = params
        t.org = this
        if (t.save()) { resultFactory.success(t) }
        else { resultFactory.failWithValidationErrors(t.errors) }
    }

    // Search
    // ------

    static int countSearch(String query) {
        ilikeForNameAndAddress(query).count()
    }
    static List<Organization> search(String query, Map params=[:]) {
        ilikeForNameAndAddress(query).list(params)
    }


    // Property Access
    // ---------------

    void setLocation(Location l) {
        this.location = l
        l.save()
    }
    List<Staff> getAdmins(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.ADMIN]])
    }
    List<Staff> getStaff(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.STAFF]])
    }
    List<Staff> getPending(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.PENDING]])
    }
    List<Staff> getBlocked(Map params=[:]) {
        getPeople(params + [statuses:[StaffStatus.BLOCKED]])
    }
    List<Staff> getPeople(Map params=[:]) {
        List statusEnums = Helpers.toEnumList(StaffStatus, params.statuses)
        Staff.forOrgAndStatuses(this, statusEnums).list(params) ?: []
    }
    int countPeople(Map params) {
        List statusEnums = Helpers.toEnumList(StaffStatus, params.statuses)
        Staff.forOrgAndStatuses(this, statusEnums).count()
    }
    int countTeams() {
        Team.countByOrg(this)
    }
    List<Team> getTeams(Map params=[:]) {
        Team.findAllByOrg(this, params)
    }
}
