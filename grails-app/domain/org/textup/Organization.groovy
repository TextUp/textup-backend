package org.textup

import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="Organization", description="An organization of staff members and teams.")
class Organization {

    def resultFactory

    @RestApiObjectField(description="Name of the organization")
	String name
    @RestApiObjectField(description="Location of the organization")
	Location location

    String status = Constants.ORG_PENDING

    @RestApiObjectField(
        apiFieldName   = "numAdmins",
        description    = "Number of admins this organization has",
        useForCreation = false,
        allowedType    = "Number")
    static transients = []
    static constraints = {
    	name blank:false, validator:{ val, obj ->
    		//must have unique (name, location) combination
    		if (obj.hasNameAndLocation(val, obj.location)) { ["duplicate"] }
    	}
        status blank:false, nullable:false, inList:[Constants.ORG_PENDING, Constants.ORG_REJECTED, Constants.ORG_APPROVED]
    }
    static namedQueries = {
        iLikeForNameAndAddress { String query ->
            or {
                ilike("name", query)
                location { ilike("address", query) }
            }
            eq("status", Constants.ORG_APPROVED)
        }
    }

	/*
	Has many:
		Staff
		Team
	*/

    ////////////////////
    // Helper methods //
    ////////////////////

    /*
    Staff
     */
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


    /*
    Teams
     */
    Result<Team> addTeam(Map params) {
        Team t = new Team()
        t.properties = params
        t.org = this
        if (t.save()) { resultFactory.success(t) }
        else { resultFactory.failWithValidationErrors(t.errors) }
    }
    Result deleteTeam(Team t) {
        if (t) {
            if (t.org == this) {
                try {
                    t.delete()
                    resultFactory.success()
                }
                catch (Throwable e) { resultFactory.failWithThrowable(e) }
            }
            else { resultFactory.failWithMessage("organization.error.teamDifferentOrg", [t.name]) }
        }
        else { resultFactory.failWithMessage("organization.error.teamNotFound") }
    }

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

    /////////////////////
    // Property Access //
    /////////////////////

    //For some reason, saving the location here is okay
    //but saving in the setteraves many duplicates for
    //when adding numbers as strings
    void setLocation(Location l) {
        this.location = l
        l.save()
    }

    /*
    Staff
     */
    List<Staff> getAdmins(Map params=[:]) { getPeople(params + [status:[Constants.STATUS_ADMIN]]) }
    List<Staff> getStaff(Map params=[:]) { getPeople(params + [status:[Constants.STATUS_STAFF]]) }
    List<Staff> getPending(Map params=[:]) { getPeople(params + [status:[Constants.STATUS_PENDING]]) }
    List<Staff> getBlocked(Map params=[:]) { getPeople(params + [status:[Constants.STATUS_BLOCKED]]) }
    List<Staff> getPeople(Map params=[:]) {
        Staff.forOrgAndStatuses(this, Helpers.toList(params.status)).list(params) ?: []
    }
    int countPeople(Map params) {
        Staff.forOrgAndStatuses(this, Helpers.toList(params.status)).count()
    }

    /*
    Teams
     */
    List<Team> getTeams(Map params=[:]) { Team.forOrg(this).list(params) }
    int countTeams() { Team.forOrg(this).count() }
}
